package com.endava.cats.io;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.FuzzingResult;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.OpenApiUtils;
import com.google.common.base.Splitter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Passively records resource identifiers returned by successful requests and reuses them in later requests.
 * No additional service calls are made by this component.
 */
@ApplicationScoped
public class RuntimeResourcePool {
    private static final int MAX_RESOURCES = 500;
    private static final int MAX_COLLECTION_ITEMS = 50;
    private static final int MAX_SUCCESSFUL_BASELINES = 500;
    private static final int MAX_CORRELATION_FEEDBACK = 5_000;
    private static final int MAX_OPERATION_DIAGNOSTICS = 2_000;
    private static final int MAX_EXCHANGES = 256;
    private static final int MAX_EXCHANGES_PER_OPERATION = 4;
    private static final int MAX_EXCHANGE_BODY_LENGTH = 32_768;
    private static final int MAX_EXCHANGE_HEADER_LENGTH = 2_048;
    private static final int MAX_FEEDBACK_REWARD = 500;
    private static final int MIN_FEEDBACK_PENALTY = -5_000;
    private static final int SUCCESS_REWARD = 100;
    private static final int NOT_FOUND_PENALTY = -1_000;
    private static final int CONFLICT_PENALTY = -200;
    private static final Pattern SEPARATED_ID = Pattern.compile("(?i).*(?:_|-)(?:id|uuid|guid|token|username|address)(?:s|es)?$");
    private static final Pattern CAMEL_IDENTIFIER = Pattern.compile(
            ".*(?:Id|ID|Uuid|UUID|Guid|GUID|Token|TOKEN|Username|USERNAME|Address|ADDRESS)(?:s|S|es|ES)?$");
    private static final Pattern WORD_BOUNDARY_SPLITTER = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[_\\-]+");
    private static final Pattern UUID_VALUE = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]");
    private static final Splitter SEMANTIC_PATH_SPLITTER = Splitter.on(Pattern.compile("[.#]")).omitEmptyStrings();
    private static final Splitter FIELD_PATH_SPLITTER = Splitter.on('#').omitEmptyStrings();
    private static final Set<String> SIMPLE_IDENTIFIERS = Set.of("id", "uuid", "guid");
    private static final Set<String> SIMPLE_PLURAL_IDENTIFIERS = Set.of("ids", "uuids", "guids");
    private static final Set<String> SELF_IDENTITY_SEGMENTS = Set.of("me", "self", "current", "whoami");
    private static final Map<String, String> IRREGULAR_SINGULARS = Map.of(
            "children", "child",
            "men", "man",
            "people", "person",
            "statuses", "status",
            "women", "woman");

    private final ProcessingArguments processingArguments;
    private final FilesArguments filesArguments;
    private final List<ResourceInstance> resources = new ArrayList<>();
    private final Map<SuccessfulBaselineKey, String> successfulRequestBaselines = new LinkedHashMap<>();
    private final Map<CorrelationFeedbackKey, Integer> correlationFeedback = new LinkedHashMap<>();
    private final Map<ResolvedRequest, Map<ResourceCorrelation, Long>> pendingCorrelationSources = new IdentityHashMap<>();
    private final Map<OperationKey, OperationDiagnostics> operationDiagnostics = new LinkedHashMap<>();
    private final ArrayDeque<SuccessfulExchange> successfulExchanges = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong();

    /**
     * Creates a runtime resource pool.
     *
     * @param processingArguments processing configuration
     * @param filesArguments      user supplied reference and URL data
     */
    @Inject
    public RuntimeResourcePool(ProcessingArguments processingArguments, FilesArguments filesArguments) {
        this.processingArguments = processingArguments;
        this.filesArguments = filesArguments;
    }

    /**
     * Enriches identifier-like request fields with values recorded from previous successful responses.
     * Explicit refData/urlParams and deliberate fuzzing always take precedence.
     *
     * @param data             service call data
     * @param processedPayload payload after explicit refData replacement
     * @return enriched payloads and correlations that were actually applied
     */
    public synchronized ResolvedRequest enrich(ServiceData data, String processedPayload) {
        if (!processingArguments.isReuseSuccessfulResources() || !data.isReplaceRefData() || resources.isEmpty()) {
            return new ResolvedRequest(processedPayload, data.getPathParamsPayload(), List.of());
        }

        List<ResourceCorrelation> correlations = new ArrayList<>();
        Map<ResourceCorrelation, Long> correlationSources = new LinkedHashMap<>();
        Map<String, ResourceInstance> requestBindings = new LinkedHashMap<>();
        String enrichedPathParams = enrichJson(data.getPathParamsPayload(), data, correlations, correlationSources,
                requestBindings, true);
        String enrichedPayload = enrichJson(processedPayload, data, correlations, correlationSources, requestBindings, false);
        ResolvedRequest resolvedRequest = new ResolvedRequest(enrichedPayload, enrichedPathParams, List.copyOf(correlations));
        if (!correlationSources.isEmpty()) {
            pendingCorrelationSources.put(resolvedRequest, Map.copyOf(correlationSources));
            while (pendingCorrelationSources.size() > MAX_CORRELATION_FEEDBACK) {
                pendingCorrelationSources.remove(pendingCorrelationSources.keySet().iterator().next());
            }
        }
        return resolvedRequest;
    }

    /**
     * Returns the payload enriched with captured runtime resource identifiers without registering pending feedback.
     *
     * @param data             service call data
     * @param processedPayload payload to enrich
     * @return enriched payload
     */
    public synchronized String previewEnrichedPayload(ServiceData data, String processedPayload) {
        if (!processingArguments.isReuseSuccessfulResources() || !data.isReplaceRefData() || resources.isEmpty()) {
            return processedPayload;
        }
        List<ResourceCorrelation> correlations = new ArrayList<>();
        Map<ResourceCorrelation, Long> correlationSources = new LinkedHashMap<>();
        Map<String, ResourceInstance> requestBindings = new LinkedHashMap<>();
        enrichJson(data.getPathParamsPayload(), data, correlations, correlationSources, requestBindings, true);
        return enrichJson(processedPayload, data, correlations, correlationSources, requestBindings, false);
    }

    synchronized Map<String, String> resolvePathParameters(ServiceData data) {
        if (!processingArguments.isReuseSuccessfulResources() || data.getHttpMethod() != HttpMethod.DELETE ||
                !data.isReplaceRefData() || !data.isReplaceUrlParams() || resources.isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolvedParameters = new LinkedHashMap<>();
        Map<String, ResourceInstance> requestBindings = new LinkedHashMap<>();
        ResourceInstance[] pathBoundResource = new ResourceInstance[1];
        List<String> pathVariables = new ArrayList<>(OpenApiUtils.getPathVariables(data.getRelativePath()));
        for (int i = pathVariables.size() - 1; i >= 0; i--) {
            String name = pathVariables.get(i);
            TargetField target = new TargetField(name, name, RequestTarget.Location.PATH,
                    pathParameterShape(data, name));
            if (data.isFuzzedField(name, RequestTarget.Location.PATH) || hasExplicitValue(data, target)) {
                continue;
            }
            String bindingKey = bindingKey(data.getRelativePath(), target);
            ResourceInstance bound = requestBindings.getOrDefault(bindingKey, pathBoundResource[0]);
            resolve(data.getRelativePath(), target, bound, false, data.getHttpMethod()).ifPresent(value -> {
                resolvedParameters.put(name, String.valueOf(value.value()));
                requestBindings.put(bindingKey, value.resource());
                if (pathBoundResource[0] == null) {
                    pathBoundResource[0] = value.resource();
                }
            });
        }
        return Map.copyOf(resolvedParameters);
    }

    synchronized ResolvedHeaders enrichHeaders(ServiceData data, List<KeyValuePair<String, Object>> headers) {
        return enrichHeaders(data, headers, null);
    }

    synchronized ResolvedHeaders enrichHeaders(ServiceData data, List<KeyValuePair<String, Object>> headers,
                                                ResolvedRequest resolvedRequest) {
        if (!processingArguments.isReuseSuccessfulResources() || !data.isReplaceRefData() || resources.isEmpty()) {
            return new ResolvedHeaders(List.copyOf(headers), List.of());
        }
        List<KeyValuePair<String, Object>> enriched = new ArrayList<>(headers);
        List<ResourceCorrelation> correlations = new ArrayList<>();
        Map<String, ResourceInstance> requestBindings = new LinkedHashMap<>();
        ResourceInstance pathResource = resolvedPathResource(data, resolvedRequest).orElse(null);
        if (resolvedRequest != null && !OpenApiUtils.getPathVariables(data.getRelativePath()).isEmpty() &&
                pathResource == null) {
            return new ResolvedHeaders(List.copyOf(headers), List.of());
        }
        headers.stream().map(KeyValuePair::getKey).distinct()
                .forEach(name -> enrichHeader(data, headers, name, pathResource, requestBindings, enriched, correlations));
        return new ResolvedHeaders(List.copyOf(enriched), List.copyOf(correlations));
    }

    private void enrichHeader(ServiceData data, List<KeyValuePair<String, Object>> headers, String name,
                              ResourceInstance pathResource, Map<String, ResourceInstance> requestBindings,
                              List<KeyValuePair<String, Object>> enriched, List<ResourceCorrelation> correlations) {
        if (hasExplicitHeader(data, name)) {
            return;
        }
        Object currentValue = headers.stream().filter(header -> header.getKey().equalsIgnoreCase(name))
                .map(KeyValuePair::getValue).findFirst().orElse("");
        TargetField target = new TargetField(name, name, RequestTarget.Location.HEADER, valueShape(currentValue));
        String bindingKey = bindingKey(data.getRelativePath(), target);
        ResourceInstance boundResource = pathResource == null ? requestBindings.get(bindingKey) : pathResource;
        resolve(data.getRelativePath(), target, boundResource, pathResource != null).ifPresent(value -> {
            Object replacement = data.isFuzzedHeader(name)
                    ? FuzzingStrategy.mergeFuzzing(currentValue, value.value()) : value.value();
            if (Objects.equals(currentValue, replacement)) {
                return;
            }
            for (int i = 0; i < enriched.size(); i++) {
                KeyValuePair<String, Object> header = enriched.get(i);
                if (header.getKey().equalsIgnoreCase(name)) {
                    enriched.set(i, new KeyValuePair<>(header.getKey(), replacement));
                }
            }
            requestBindings.put(bindingKey, value.resource());
            correlations.add(ResourceCorrelation.builder()
                    .sourceMethod(value.resource().sourceMethod().name())
                    .sourcePath(value.resource().sourcePath())
                    .sourceLocation(value.storedValue().sourceLocation())
                    .target(RequestTarget.header(name))
                    .value(value.value())
                    .build());
        });
    }

    private Optional<ResourceInstance> resolvedPathResource(ServiceData data, ResolvedRequest resolvedRequest) {
        Map<ResourceCorrelation, Long> sources = pendingCorrelationSources.get(resolvedRequest);
        if (sources == null) {
            return Optional.empty();
        }
        Map<String, Long> pathResourceByParam = new LinkedHashMap<>();
        sources.forEach((correlation, resourceSequence) -> {
            if (correlation.getTarget().location() == RequestTarget.Location.PATH) {
                pathResourceByParam.put(correlation.getTarget().name(), resourceSequence);
            }
        });
        if (pathResourceByParam.isEmpty()) {
            return Optional.empty();
        }
        Long resourceSequence = selectLeafPathResourceSequence(data.getRelativePath(), pathResourceByParam);
        if (resourceSequence == null) {
            return Optional.empty();
        }
        return resources.stream().filter(ResourceInstance::active)
                .filter(resource -> resource.sequence() == resourceSequence).findFirst();
    }

    private Long selectLeafPathResourceSequence(String relativePath, Map<String, Long> pathResourceByParam) {
        if (pathResourceByParam.size() == 1) {
            return pathResourceByParam.values().iterator().next();
        }
        List<String> variables = new ArrayList<>(OpenApiUtils.getPathVariables(relativePath));
        for (int i = variables.size() - 1; i >= 0; i--) {
            String variable = variables.get(i);
            for (Map.Entry<String, Long> entry : pathResourceByParam.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(variable)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    synchronized String applySuccessfulRequestBaseline(ServiceData data) {
        String currentPayload = data.getPayload();
        if (!processingArguments.isReuseSuccessfulResources() || !data.isReplaceRefData() || !data.isValidJson() ||
                !data.getPayloadFormat().supportsNamedFields() || !HttpMethod.requiresBody(data.getHttpMethod()) ||
                !JsonUtils.isValidJson(currentPayload)) {
            return currentPayload;
        }
        Optional<SuccessfulBaselineKey> key = successfulBaselineKey(data);
        String baseline = key.map(successfulRequestBaselines::get).orElse(null);
        String originalPayload = originalPayload(data);
        if (baseline == null || !JsonUtils.isValidJson(originalPayload) || !JsonUtils.isValidJson(baseline)) {
            return currentPayload;
        }
        try {
            JsonElement original = JsonUtils.parseAsJsonElement(originalPayload);
            JsonElement current = JsonUtils.parseAsJsonElement(currentPayload);
            JsonElement successful = JsonUtils.parseAsJsonElement(baseline);
            return mergeSuccessfulBaseline(original, current, successful).toString();
        } catch (RuntimeException _) {
            return currentPayload;
        }
    }

    /**
     * Observes a completed request. Successful POST/PUT responses and collection GET responses contribute resources;
     * successful DELETE requests invalidate matching resources.
     *
     * @param data     service call data
     * @param request  final request sent to the service
     * @param response response returned by the service
     */
    public synchronized void observe(ServiceData data, CatsRequest request, CatsResponse response) {
        observe(data, request, response, null);
    }

    synchronized void observe(ServiceData data, CatsRequest request, CatsResponse response,
                              ResolvedRequest resolvedRequest) {
        if (!processingArguments.isReuseSuccessfulResources()) {
            return;
        }
        recordOperationDiagnostics(data, request, response, resolvedRequest);
        updateCorrelationFeedback(data, response, resolvedRequest);
        if (!ResponseCodeFamily.is2xxCode(response.getResponseCode())) {
            return;
        }
        recordSuccessfulExchange(data, request, response);
        storeSuccessfulRequestBaseline(data, request, resolvedRequest);
        if (data.getHttpMethod() == HttpMethod.DELETE) {
            markDeleted(data, request.getUrl());
            return;
        }
        if (!isResourceProducingMethod(data.getHttpMethod())) {
            return;
        }
        Map<String, StoredValue> pathValues = extractPathValues(data.getRelativePath(), request.getUrl());
        String effectiveRequestPayload = effectiveJsonPayload(request, resolvedRequest);
        List<JsonObject> requestObjects = HttpMethod.requiresBody(data.getHttpMethod()) && !isRequestBodyFuzzed(data)
                ? requestObjects(effectiveRequestPayload) : List.of();
        if (response.isBodyTruncated()) {
            addFromLocation(data, response, pathValues, requestObjects);
            return;
        }
        if (isUnmatchedCollectionGet(data, response)) {
            addResourceFromReusableIndicators(data, response, pathValues);
            return;
        }
        observeResponseObjects(data, effectiveRequestPayload, response, pathValues, requestObjects);
    }

    private static boolean isResourceProducingMethod(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH ||
                method == HttpMethod.GET || method == HttpMethod.HEAD;
    }

    private boolean isUnmatchedCollectionGet(ServiceData data, CatsResponse response) {
        return data.getHttpMethod() == HttpMethod.GET &&
                (!isCollectionPath(data.getRelativePath()) || !isCollectionResponse(response.getBody()));
    }

    private void addResourceFromReusableIndicators(ServiceData data, CatsResponse response, Map<String, StoredValue> pathValues) {
        boolean reusableHeaders = collectResponseHeaders(response, pathValues);
        boolean reusableItemPath = !isCollectionPath(data.getRelativePath()) && !pathValues.isEmpty() &&
                OpenApiUtils.getPathVariables(data.getRelativePath()).stream()
                        .noneMatch(name -> data.isFuzzedField(name, RequestTarget.Location.PATH) ||
                                wasModifiedByFuzzer(data, name, false));
        if (isCollectionPath(data.getRelativePath()) && JsonUtils.isValidJson(response.getBody())) {
            try {
                JsonElement root = JsonUtils.parseAsJsonElement(response.getBody());
                if (root != null && root.isJsonObject()) {
                    collectPrimitiveValues(root.getAsJsonObject(), "$.", "response.body.", pathValues);
                    if (!pathValues.isEmpty()) {
                        addResource(data, pathValues);
                        return;
                    }
                }
            } catch (RuntimeException _) {
                // ignore invalid json
            }
        }
        if (reusableHeaders || reusableItemPath) {
            addResource(data, pathValues);
        }
    }

    private void observeResponseObjects(ServiceData data, String effectiveRequestPayload, CatsResponse response,
                                        Map<String, StoredValue> pathValues, List<JsonObject> requestObjects) {
        List<ResponseObject> responseObjects = responseObjects(response.getBody(), data.getHttpMethod() == HttpMethod.GET);
        if (responseObjects.isEmpty()) {
            addFromLocation(data, response, pathValues, requestObjects);
            return;
        }
        boolean singleResponseObject = responseObjects.size() == 1;
        Optional<StoredValue> locationIdentifier = singleResponseObject ? locationIdentifier(response) : Optional.empty();
        boolean alignedRequestObjects = alignedObjectPayloads(effectiveRequestPayload, response.getBody(),
                requestObjects.size(), responseObjects.size());
        int limit = Math.min(responseObjects.size(), MAX_COLLECTION_ITEMS);
        for (int i = 0; i < limit; i++) {
            Map<String, StoredValue> values = new LinkedHashMap<>(pathValues);
            ResponseObject responseObject = responseObjects.get(i);
            collectPrimitiveValues(responseObject.value(), responseObject.sourcePrefix(), "response.body.", values);
            if (alignedRequestObjects) {
                collectPrimitiveValues(requestObjects.get(i), "$.", "request.body.", values);
            }
            if (singleResponseObject) {
                collectResponseHeaders(response, values);
            }
            collectKeyedMapIdentifier(data.getRelativePath(), responseObject.value(), values);
            assignFallbackIdentifier(data.getRelativePath(), locationIdentifier, values);
            addResource(data, values);
        }
        if (!singleResponseObject) {
            Map<String, StoredValue> headerValues = new LinkedHashMap<>(pathValues);
            if (collectResponseHeaders(response, headerValues)) {
                addResource(data, headerValues);
            }
        }
    }

    private void assignFallbackIdentifier(String relativePath, Optional<StoredValue> locationIdentifier,
                                          Map<String, StoredValue> values) {
        if (hasResourceIdentifier(values, relativePath)) {
            return;
        }
        locationIdentifier.or(() -> wrapperIdentifier(values))
                .ifPresent(value -> values.put("id", value));
    }

    private void collectKeyedMapIdentifier(String relativePath, JsonObject root, Map<String, StoredValue> values) {
        String resource = resourceName(relativePath);
        JsonElement mapCandidate = root.get(resource);
        if (mapCandidate != null && mapCandidate.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : mapCandidate.getAsJsonObject().entrySet()) {
                if (entry.getValue() != null && entry.getValue().isJsonObject() && StringUtils.isNotBlank(entry.getKey())) {
                    values.putIfAbsent("$." + resource + ".@key", new StoredValue(entry.getKey(),
                            "response.body.$." + resource + "." + entry.getKey(),
                            Set.of("id", "name", resource + "id", singular(resource) + "id")));
                    break;
                }
            }
        }
    }

    private Optional<StoredValue> wrapperIdentifier(Map<String, StoredValue> values) {
        List<Map.Entry<String, StoredValue>> topLevelEntries = values.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("$.") && isTopLevelResponseKey(entry.getKey()))
                .toList();
        List<Map.Entry<String, StoredValue>> candidates = topLevelEntries.stream()
                .filter(entry -> !isMetadataKey(normalize(entry.getKey())))
                .filter(entry -> entry.getValue() != null && entry.getValue().value() != null
                        && !(entry.getValue().value() instanceof Boolean)
                        && StringUtils.isNotBlank(String.valueOf(entry.getValue().value())))
                .toList();
        if (candidates.size() == 1 && (topLevelEntries.size() >= 2 || candidates.getFirst().getKey().endsWith("[]"))) {
            StoredValue candidate = candidates.getFirst().getValue();
            return Optional.of(new StoredValue(candidate.value(), candidate.sourceLocation(), Set.of("id")));
        }
        return Optional.empty();
    }

    private boolean isMetadataKey(String normalizedKey) {
        return normalizedKey.contains("time") || normalizedKey.contains("date") ||
                normalizedKey.contains("status") || normalizedKey.contains("message") ||
                normalizedKey.contains("error") || normalizedKey.equals("code") ||
                normalizedKey.equals("version");
    }

    /** Clears all values captured by the current run. */
    public synchronized void clear() {
        resources.clear();
        successfulRequestBaselines.clear();
        correlationFeedback.clear();
        pendingCorrelationSources.clear();
        operationDiagnostics.clear();
        successfulExchanges.clear();
        sequence.set(0);
    }

    /**
     * Returns the operations that never succeeded with an unmodified (non-fuzzed) request and that rely on
     * identifier parameters which could not be resolved from earlier responses, or whose resolved values
     * always returned 404/410. Operations are returned in the order they were first observed.
     *
     * @return operations with unresolved identifier parameters
     */
    public synchronized List<UnresolvedOperation> unresolvedOperations() {
        List<UnresolvedOperation> result = new ArrayList<>();
        operationDiagnostics.forEach((key, diagnostics) -> {
            if (diagnostics.successes > 0 || diagnostics.onlyAuthFailures()) {
                return;
            }
            List<UnresolvedParameter> parameters = new ArrayList<>();
            diagnostics.parameters.forEach((target, stats) -> stats.reason().ifPresent(reason ->
                    parameters.add(new UnresolvedParameter(target, reason, producerOutcome(key.path(), target)))));
            if (!parameters.isEmpty()) {
                result.add(new UnresolvedOperation(diagnostics.outcome(key), List.copyOf(parameters)));
            }
        });
        return List.copyOf(result);
    }

    /**
     * Checks if an unmodified (non-fuzzed) request to the given operation returned 2xx earlier in the run.
     * Always returns false when {@code --reuseSuccessfulResources} is disabled, as outcomes are not recorded.
     *
     * @param path   contract path
     * @param method HTTP method
     * @return true if the operation succeeded at least once with an unmodified request
     */
    public synchronized boolean hasSucceededWithUnmodifiedRequest(String path, HttpMethod method) {
        OperationDiagnostics diagnostics = operationDiagnostics.get(new OperationKey(path, method));
        return diagnostics != null && diagnostics.successes > 0;
    }

    /**
     * Returns the bounded successful, unmodified exchanges observed for one documented operation in this run.
     * Request headers are deliberately not retained; request/response bodies stay in memory and are length-limited.
     *
     * @param path contract path
     * @param method HTTP method
     * @return immutable snapshot in observation order
     */
    public synchronized List<SuccessfulExchange> successfulExchanges(String path, HttpMethod method) {
        return successfulExchanges.stream()
                .filter(exchange -> exchange.path().equals(path) && exchange.method() == method)
                .toList();
    }

    /**
     * Returns an active, non-fuzzed, unpenalized identifier value for the given path parameter that differs from
     * {@code currentValue}.
     *
     * @param targetPath    contract path template
     * @param parameterName path parameter name
     * @param currentValue  currently observed value to exclude
     * @return alternative identifier value, if one was observed
     */
    public synchronized Optional<String> alternativePathParameterValue(String targetPath, String parameterName,
                                                                       String currentValue) {
        if (StringUtils.isBlank(targetPath) || StringUtils.isBlank(parameterName) || StringUtils.isBlank(currentValue)) {
            return Optional.empty();
        }
        TargetField target = new TargetField(parameterName, parameterName, RequestTarget.Location.PATH,
                stringShape(currentValue));
        Set<String> targetAliases = targetAliases(targetPath, target);
        List<ResolvedValue> candidates = resources.stream()
                .filter(ResourceInstance::active)
                .filter(resource -> !resource.fuzzed())
                .filter(resource -> correlationFeedbackScore(resource, targetPath, target) >= 0)
                .flatMap(resource -> resource.values().values().stream()
                        .filter(value -> value.aliases().stream().anyMatch(targetAliases::contains))
                        .filter(value -> !sameValue(currentValue, value.value()))
                        .map(value -> new ResolvedValue(resource, value,
                                pathAffinity(resource.sourcePath(), targetPath) * 1_000 +
                                        Math.min(50, matchSpecificity(value, targetAliases)) * 10 +
                                        sourcePriority(resource) + storedValuePriority(value) +
                                        correlationFeedbackScore(resource, targetPath, target))))
                .filter(candidate -> semanticConfidence(candidate, targetPath, targetAliases) >= 2)
                .filter(candidate -> compatibilityQuality(candidate.storedValue().value(), target) > 0)
                .toList();
        int bestQuality = candidates.stream()
                .mapToInt(candidate -> candidateQuality(candidate, targetPath, target, targetAliases, null))
                .max().orElse(Integer.MIN_VALUE);
        return candidates.stream()
                .filter(candidate -> candidateQuality(candidate, targetPath, target, targetAliases, null) == bestQuality)
                .max(Comparator.comparingInt(ResolvedValue::score)
                        .thenComparingLong(value -> value.resource().sequence()))
                .map(ResolvedValue::value)
                .map(String::valueOf)
                .filter(value -> StringUtils.isNotBlank(value) && !value.contains("/"));
    }

    /** Captures only bounded, unmodified successful exchanges; request headers are never retained. */
    private void recordSuccessfulExchange(ServiceData data, CatsRequest request, CatsResponse response) {
        if (!isEligibleExchange(data, request, response)) {
            return;
        }
        OperationKey key = new OperationKey(data.getRelativePath(), data.getHttpMethod());
        SuccessfulExchange exchange = new SuccessfulExchange(key.path(), key.method(), request.getUrl(),
                request.getPayload(), response.getResponseCode(), response.getBody(),
                boundedHeader(response, "Location"), boundedHeader(response, "ETag"));
        evictOldestExchangeForOperationIfFull(key);
        successfulExchanges.addLast(exchange);
        if (successfulExchanges.size() > MAX_EXCHANGES) {
            successfulExchanges.removeFirst();
        }
    }

    private boolean isEligibleExchange(ServiceData data, CatsRequest request, CatsResponse response) {
        return isUnmodifiedContractRequest(data) && !response.isBodyTruncated() &&
                Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH).contains(data.getHttpMethod()) &&
                response.getResponseCode() != 202 && StringUtils.isNotBlank(request.getUrl()) &&
                request.getUrl().length() <= MAX_EXCHANGE_HEADER_LENGTH &&
                !tooLong(request.getPayload()) && !tooLong(response.getBody());
    }

    private void evictOldestExchangeForOperationIfFull(OperationKey key) {
        List<SuccessfulExchange> existing = successfulExchanges(key.path(), key.method());
        if (existing.size() >= MAX_EXCHANGES_PER_OPERATION) {
            successfulExchanges.remove(existing.getFirst());
        }
    }

    private boolean tooLong(String value) {
        return value != null && value.length() > MAX_EXCHANGE_BODY_LENGTH;
    }

    private String boundedHeader(CatsResponse response, String name) {
        KeyValuePair<String, String> header = response.getHeader(name);
        return header == null || header.getValue() == null || header.getValue().length() > MAX_EXCHANGE_HEADER_LENGTH
                ? null : header.getValue();
    }

    /**
     * Small in-memory evidence snapshot for second-phase checks. Request headers are never copied into this record.
     *
     * @param path OpenAPI path template
     * @param method operation method
     * @param url final request URL
     * @param requestBody final request body, bounded in size
     * @param status successful response status
     * @param responseBody captured response body, bounded in size
     * @param location Location response header, if present and short enough
     * @param etag ETag response header, if present and short enough
     */
    public record SuccessfulExchange(String path, HttpMethod method, String url, String requestBody,
                                     int status, String responseBody, String location, String etag) {
        @Override
        public String toString() {
            return "SuccessfulExchange[method=%s, path=%s, status=%d]".formatted(method, path, status);
        }
    }

    private OperationOutcome producerOutcome(String path, RequestTarget target) {
        if (target.location() == RequestTarget.Location.PATH) {
            List<String> segments = pathSegments(path);
            int index = segments.indexOf("{" + target.name() + "}");
            if (index > 0) {
                OperationKey producerKey = new OperationKey("/" + String.join("/", segments.subList(0, index)), HttpMethod.POST);
                OperationDiagnostics directProducer = operationDiagnostics.get(producerKey);
                if (directProducer != null) {
                    return directProducer.outcome(producerKey);
                }
            }
        }
        String leafName = target.name().substring(target.name().lastIndexOf('#') + 1);
        Set<String> qualifiedAliases = semanticAliases(target.name(), leafName).stream()
                .filter(alias -> !SIMPLE_IDENTIFIERS.contains(alias))
                .collect(Collectors.toSet());
        if (qualifiedAliases.isEmpty()) {
            return null;
        }
        return operationDiagnostics.entrySet().stream()
                .filter(entry -> entry.getKey().method() == HttpMethod.POST && !entry.getKey().path().equals(path))
                .filter(entry -> isCollectionPath(entry.getKey().path()))
                .filter(entry -> primaryResourceIdentifierAliases(entry.getKey().path()).stream().anyMatch(qualifiedAliases::contains))
                .findFirst()
                .map(entry -> entry.getValue().outcome(entry.getKey()))
                .orElse(null);
    }

    private void recordOperationDiagnostics(ServiceData data, CatsRequest request, CatsResponse response,
                                            ResolvedRequest resolvedRequest) {
        if (!isUnmodifiedContractRequest(data)) {
            return;
        }
        OperationKey key = new OperationKey(data.getRelativePath(), data.getHttpMethod());
        OperationDiagnostics diagnostics = operationDiagnostics.get(key);
        if (diagnostics == null) {
            if (operationDiagnostics.size() >= MAX_OPERATION_DIAGNOSTICS) {
                return;
            }
            diagnostics = new OperationDiagnostics();
            operationDiagnostics.put(key, diagnostics);
        }
        int responseCode = response.getResponseCode();
        diagnostics.register(responseCode);
        Set<RequestTarget> correlatedTargets = Optional.ofNullable(resolvedRequest).map(ResolvedRequest::correlations)
                .orElse(List.of()).stream().map(ResourceCorrelation::getTarget).collect(Collectors.toSet());
        Map<String, StoredValue> actualPathValues = extractPathValues(data.getRelativePath(), request.getUrl());
        for (TargetField target : diagnosticTargets(data)) {
            RequestTarget requestTarget = new RequestTarget(target.location(), target.path());
            Optional<Boolean> resolved = correlatedTargets.contains(requestTarget) ? Optional.of(true) :
                    valueReplaced(data, request, resolvedRequest, target, actualPathValues);
            OperationDiagnostics current = diagnostics;
            resolved.ifPresent(value -> current.recordParameter(requestTarget, value, responseCode));
        }
    }

    private String effectiveJsonPayload(CatsRequest request, ResolvedRequest resolvedRequest) {
        if (JsonUtils.isValidJson(request.getPayload())) {
            return request.getPayload();
        }
        if (resolvedRequest != null && JsonUtils.isValidJson(resolvedRequest.payload())) {
            return resolvedRequest.payload();
        }
        return request.getPayload();
    }

    private boolean isUnmodifiedContractRequest(ServiceData data) {
        boolean contractPath = StringUtils.isBlank(data.getContractPath()) ||
                data.getContractPath().equals(data.getRelativePath());
        return contractPath && data.getRelativePath() != null && data.getHttpMethod() != null &&
                data.isReplaceRefData() && data.isReplaceUrlParams() && data.isAddUserHeaders() &&
                Optional.ofNullable(data.getSkippedHeaders()).orElse(Set.of()).isEmpty() && !isFuzzedRequest(data);
    }

    private List<TargetField> diagnosticTargets(ServiceData data) {
        Map<RequestTarget, TargetField> targets = new LinkedHashMap<>();
        List<TargetField> candidates = new ArrayList<>();
        if (JsonUtils.isValidJson(data.getPayload())) {
            candidates.addAll(reusableTargets(data.getPayload(), data, false));
        }
        if (JsonUtils.isValidJson(data.getPathParamsPayload())) {
            candidates.addAll(reusableTargets(data.getPathParamsPayload(), data, true));
        }
        candidates.stream()
                .filter(target -> target.location() == RequestTarget.Location.PATH || isIdentifierName(target.name()))
                .filter(target -> !hasExplicitValue(data, target) && !shouldSkipOwnPostIdentifier(data, target))
                .forEach(target -> targets.putIfAbsent(new RequestTarget(target.location(), target.path()), target));
        return List.copyOf(targets.values());
    }

    private Optional<Boolean> valueReplaced(ServiceData data, CatsRequest request, ResolvedRequest resolvedRequest,
                                            TargetField target, Map<String, StoredValue> actualPathValues) {
        if (target.location() == RequestTarget.Location.PATH) {
            StoredValue actual = actualPathValues.get(target.name());
            Object generated = generatedPathValue(data, target.name());
            if (actual == null || generated == null) {
                return Optional.empty();
            }
            return Optional.of(!sameValue(generated, actual.value()));
        }
        String effectivePayload = effectiveJsonPayload(request, resolvedRequest);
        if (!JsonUtils.isValidJson(effectivePayload)) {
            return Optional.empty();
        }
        List<JsonElement> actual = valuesAtPath(effectivePayload, target.path());
        if (actual.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(!actual.equals(valuesAtPath(data.getPayload(), target.path())));
    }

    private Object generatedPathValue(ServiceData data, String name) {
        for (String payload : List.of(Optional.ofNullable(data.getPathParamsPayload()).orElse(""),
                Optional.ofNullable(data.getPayload()).orElse(""))) {
            if (!JsonUtils.isValidJson(payload)) {
                continue;
            }
            Object value = JsonUtils.getVariableFromJson(payload, name);
            if (!JsonUtils.isNotSet(String.valueOf(value))) {
                return value;
            }
        }
        return null;
    }

    private boolean sameValue(Object generated, Object actual) {
        String generatedValue = String.valueOf(generated);
        String actualValue = String.valueOf(actual);
        if (generatedValue.equals(actualValue)) {
            return true;
        }
        try {
            return new BigDecimal(generatedValue).compareTo(new BigDecimal(actualValue)) == 0;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    synchronized void discard(ResolvedRequest resolvedRequest) {
        pendingCorrelationSources.remove(resolvedRequest);
    }

    private void storeSuccessfulRequestBaseline(ServiceData data, CatsRequest request, ResolvedRequest resolvedRequest) {
        String effectivePayload = effectiveJsonPayload(request, resolvedRequest);
        boolean requiredOnlyFallback = data.getAllMutationTargets().equals(List.of(RequestTarget.requestBody()));
        if ((isRequestBodyFuzzed(data) && !requiredOnlyFallback) || !data.isReplaceRefData() || !data.isValidJson() ||
                !data.getPayloadFormat().supportsNamedFields() || !HttpMethod.requiresBody(data.getHttpMethod()) ||
                !JsonUtils.isValidJson(effectivePayload)) {
            return;
        }
        successfulBaselineKey(data).ifPresent(key -> {
            if (requiredOnlyFallback && successfulRequestBaselines.containsKey(key)) {
                return;
            }
            successfulRequestBaselines.put(key, JsonUtils.parseAsJsonElement(effectivePayload).toString());
            while (successfulRequestBaselines.size() > MAX_SUCCESSFUL_BASELINES) {
                successfulRequestBaselines.remove(successfulRequestBaselines.keySet().iterator().next());
            }
        });
    }

    private Optional<SuccessfulBaselineKey> successfulBaselineKey(ServiceData data) {
        String originalPayload = originalPayload(data);
        if (!JsonUtils.isValidJson(originalPayload)) {
            return Optional.empty();
        }
        String path = StringUtils.defaultIfBlank(data.getContractPath(), data.getRelativePath());
        String contentType = Optional.ofNullable(data.getContentType()).orElse("").split(";", 2)[0]
                .trim().toLowerCase(Locale.ROOT);
        String variant = JsonUtils.parseAsJsonElement(originalPayload).toString();
        return Optional.of(new SuccessfulBaselineKey(path, data.getHttpMethod(), contentType, variant));
    }

    private String originalPayload(ServiceData data) {
        return StringUtils.defaultIfBlank(data.getOriginalPayload(), data.getPayload());
    }

    private JsonElement mergeSuccessfulBaseline(JsonElement original, JsonElement current, JsonElement successful) {
        if (original.equals(current)) {
            return successful.deepCopy();
        }
        if (original.isJsonObject() && current.isJsonObject() && successful.isJsonObject()) {
            return mergeSuccessfulObject(original.getAsJsonObject(), current.getAsJsonObject(), successful.getAsJsonObject());
        }
        if (original.isJsonArray() && current.isJsonArray() && successful.isJsonArray()) {
            return mergeSuccessfulArray(original.getAsJsonArray(), current.getAsJsonArray(), successful.getAsJsonArray());
        }
        if (current.isJsonPrimitive() && successful.isJsonPrimitive()) {
            Object merged = FuzzingStrategy.mergeFuzzing(primitiveValue(current.getAsJsonPrimitive()),
                    primitiveValue(successful.getAsJsonPrimitive()));
            return JsonUtils.parseAsJsonElement(JsonUtils.serialize(merged));
        }
        return current.deepCopy();
    }

    private JsonArray mergeSuccessfulArray(JsonArray originalArray, JsonArray currentArray, JsonArray successfulArray) {
        if (originalArray.isEmpty() || originalArray.size() != successfulArray.size()) {
            return currentArray.deepCopy();
        }
        JsonArray result = new JsonArray();
        for (int i = 0; i < currentArray.size(); i++) {
            JsonElement currentElement = currentArray.get(i);
            int matchingOriginalIndex = findMatchingElementIndex(originalArray, currentElement);
            if (matchingOriginalIndex >= 0) {
                result.add(successfulArray.get(matchingOriginalIndex).deepCopy());
            } else if (i < originalArray.size()) {
                result.add(mergeSuccessfulBaseline(originalArray.get(i), currentElement, successfulArray.get(i)));
            } else {
                result.add(currentElement.deepCopy());
            }
        }
        return result;
    }

    private int findMatchingElementIndex(JsonArray array, JsonElement target) {
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).equals(target)) {
                return i;
            }
        }
        return -1;
    }

    private JsonObject mergeSuccessfulObject(JsonObject original, JsonObject current, JsonObject successful) {
        JsonObject result = successful.deepCopy();
        original.keySet().stream().filter(key -> !current.has(key)).forEach(result::remove);
        for (Map.Entry<String, JsonElement> entry : current.entrySet()) {
            String key = entry.getKey();
            if (!original.has(key)) {
                result.add(key, entry.getValue().deepCopy());
            } else if (!original.get(key).equals(entry.getValue())) {
                JsonElement successfulValue = result.get(key);
                JsonElement merged = successfulValue == null ? entry.getValue().deepCopy() :
                        mergeSuccessfulBaseline(original.get(key), entry.getValue(), successfulValue);
                result.add(key, merged);
            }
        }
        return result;
    }

    private void updateCorrelationFeedback(ServiceData data, CatsResponse response,
                                           ResolvedRequest resolvedRequest) {
        if (resolvedRequest == null) {
            return;
        }
        Map<ResourceCorrelation, Long> correlationSources = pendingCorrelationSources.remove(resolvedRequest);
        int adjustment = feedbackAdjustment(data, response.getResponseCode());
        if (adjustment == 0 || correlationSources == null || correlationSources.isEmpty()) {
            return;
        }
        Set<CorrelationFeedbackKey> keys = new LinkedHashSet<>();
        correlationSources.forEach((correlation, resourceSequence) -> keys.add(
                new CorrelationFeedbackKey(resourceSequence, data.getRelativePath(), correlation.getTarget())));
        keys.forEach(key -> updateCorrelationFeedback(key, adjustment));
    }

    private int feedbackAdjustment(ServiceData data, int responseCode) {
        if (ResponseCodeFamily.is2xxCode(responseCode)) {
            return SUCCESS_REWARD;
        }
        if (isFuzzedRequest(data)) {
            return 0;
        }
        return switch (responseCode) {
            case 404, 410 -> NOT_FOUND_PENALTY;
            case 409, 422 -> CONFLICT_PENALTY;
            default -> 0;
        };
    }

    private void updateCorrelationFeedback(CorrelationFeedbackKey key, int adjustment) {
        correlationFeedback.compute(key, (_, current) -> {
            int previous = Optional.ofNullable(current).orElse(0);
            if (adjustment > 0) {
                return Math.min(MAX_FEEDBACK_REWARD, Math.max(0, previous) + adjustment);
            }
            return Math.max(MIN_FEEDBACK_PENALTY, Math.min(0, previous) + adjustment);
        });
        while (correlationFeedback.size() > MAX_CORRELATION_FEEDBACK) {
            correlationFeedback.remove(correlationFeedback.keySet().iterator().next());
        }
    }

    private String enrichJson(String json, ServiceData data, List<ResourceCorrelation> correlations,
                              Map<ResourceCorrelation, Long> correlationSources,
                              Map<String, ResourceInstance> requestBindings, boolean pathParametersPayload) {
        if (!pathParametersPayload && HttpMethod.requiresBody(data.getHttpMethod()) && !JsonUtils.isValidJson(json)) {
            Optional<String> rootScalar = enrichRootScalarBody(json, data, correlations, correlationSources);
            if (rootScalar.isPresent()) {
                return rootScalar.get();
            }
        }
        if (!JsonUtils.isValidJson(json)) {
            return json;
        }

        String result = json;
        ResourceInstance[] pathBoundResource = new ResourceInstance[1];
        for (TargetField target : orderPathTargetsLeafFirst(reusableTargets(json, data, pathParametersPayload), data.getRelativePath())) {
            result = enrichTargetField(result, target, data, correlations, correlationSources,
                    requestBindings, pathBoundResource, pathParametersPayload);
        }
        return result;
    }

    private String enrichTargetField(String currentJson, TargetField target, ServiceData data,
                                     List<ResourceCorrelation> correlations,
                                     Map<ResourceCorrelation, Long> correlationSources,
                                     Map<String, ResourceInstance> requestBindings,
                                     ResourceInstance[] pathBoundResource, boolean pathParametersPayload) {
        if ((target.location() == RequestTarget.Location.PATH && !data.isReplaceUrlParams()) ||
                hasExplicitValue(data, target) || shouldSkipOwnPostIdentifier(data, target)) {
            return currentJson;
        }
        String bindingKey = bindingKey(data.getRelativePath(), target);
        ResourceInstance boundResource = requestBindings.getOrDefault(bindingKey,
                target.location() == RequestTarget.Location.PATH ? pathBoundResource[0] : null);
        Optional<ResolvedValue> resolved = resolve(data.getRelativePath(), target, boundResource, false, data.getHttpMethod());
        if (resolved.isEmpty()) {
            return currentJson;
        }
        ResolvedValue value = resolved.get();
        boolean mergeFuzzing = data.isFuzzedField(target.path(), target.location()) ||
                wasModifiedByFuzzer(data, target.path(), pathParametersPayload);
        String updated;
        try {
            updated = FuzzingStrategy.replaceField(currentJson, target.path(),
                    FuzzingStrategy.replace().withData(value.value()), mergeFuzzing).json();
        } catch (RuntimeException _) {
            return currentJson;
        }
        if (!currentJson.equals(updated)) {
            ResourceCorrelation correlation = ResourceCorrelation.builder()
                    .sourceMethod(value.resource().sourceMethod().name())
                    .sourcePath(value.resource().sourcePath())
                    .sourceLocation(value.storedValue().sourceLocation())
                    .target(new RequestTarget(target.location(), target.path()))
                    .value(value.value())
                    .build();
            correlations.add(correlation);
            correlationSources.put(correlation, value.resource().sequence());
            requestBindings.put(bindingKey, value.resource());
            if (target.location() == RequestTarget.Location.PATH && pathBoundResource[0] == null) {
                pathBoundResource[0] = value.resource();
            }
        }
        return updated;
    }

    private Optional<String> enrichRootScalarBody(String json, ServiceData data, List<ResourceCorrelation> correlations,
                                                  Map<ResourceCorrelation, Long> correlationSources) {
        if (data.hasDeclaredMutation() || StringUtils.isBlank(json) || StringUtils.isBlank(data.getRelativePath())) {
            return Optional.empty();
        }
        String trimmed = json.trim().replace("\"", "");
        if (trimmed.isEmpty() || trimmed.length() > 128 || trimmed.chars().anyMatch(Character::isWhitespace)) {
            return Optional.empty();
        }
        List<String> segments = pathSegments(data.getRelativePath());
        if (segments.size() < 2 || isTemplateSegment(segments.getLast()) || isTemplateSegment(segments.get(segments.size() - 2))) {
            return Optional.empty();
        }
        String parentResource = normalize(segments.get(segments.size() - 2));
        TargetField target = new TargetField(parentResource + "Id", parentResource + "Id", RequestTarget.Location.BODY, ValueShape.STRING);
        return resolve(data.getRelativePath(), target, null).map(resolved -> {
            ResourceCorrelation correlation = ResourceCorrelation.builder()
                    .sourceMethod(resolved.resource().sourceMethod().name())
                    .sourcePath(resolved.resource().sourcePath())
                    .sourceLocation(resolved.storedValue().sourceLocation())
                    .target(RequestTarget.requestBody())
                    .value(resolved.value())
                    .build();
            correlations.add(correlation);
            correlationSources.put(correlation, resolved.resource().sequence());
            return String.valueOf(resolved.value());
        });
    }

    private List<TargetField> orderPathTargetsLeafFirst(List<TargetField> targets, String relativePath) {
        List<String> pathVariables = new ArrayList<>(OpenApiUtils.getPathVariables(relativePath));
        if (pathVariables.size() < 2) {
            return targets;
        }
        List<TargetField> ordered = new ArrayList<>(targets);
        ordered.sort(Comparator.comparingInt(target -> {
            if (target.location() != RequestTarget.Location.PATH) {
                return Integer.MAX_VALUE;
            }
            for (int i = pathVariables.size() - 1; i >= 0; i--) {
                if (pathVariables.get(i).equalsIgnoreCase(target.name())) {
                    return pathVariables.size() - 1 - i;
                }
            }
            return pathVariables.size();
        }));
        return ordered;
    }

    private List<TargetField> reusableTargets(String json, ServiceData data, boolean pathParametersPayload) {
        JsonElement root;
        try {
            root = JsonUtils.parseAsJsonElement(json);
        } catch (RuntimeException _) {
            return List.of();
        }

        List<TargetField> targets = new ArrayList<>();
        collectReusableTargets(root, "", data, pathParametersPayload, targets);
        return targets;
    }

    private void collectReusableTargets(JsonElement element, String prefix, ServiceData data,
                                        boolean pathParametersPayload, List<TargetField> targets) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (!array.isEmpty()) {
                collectReusableTargets(array.get(0), prefix, data, pathParametersPayload, targets);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "#" + entry.getKey();
            if (entry.getValue().isJsonPrimitive()) {
                RequestTarget.Location location = targetLocation(data, entry.getKey(), pathParametersPayload);
                if (location == RequestTarget.Location.PATH || location == RequestTarget.Location.QUERY ||
                        isIdentifierName(entry.getKey()) || hasSiblingPostValue(data, entry.getKey())) {
                    targets.add(new TargetField(path, entry.getKey(), location,
                            valueShape(entry.getValue().getAsJsonPrimitive())));
                }
            } else if (isPrimitiveArray(entry.getValue())) {
                JsonPrimitive firstElement = entry.getValue().getAsJsonArray().get(0).getAsJsonPrimitive();
                RequestTarget.Location location = targetLocation(data, entry.getKey(), pathParametersPayload);
                if (location == RequestTarget.Location.PATH || location == RequestTarget.Location.QUERY ||
                        isIdentifierName(entry.getKey())) {
                    targets.add(new TargetField(path, entry.getKey(), location, valueShape(firstElement)));
                }
            } else {
                collectReusableTargets(entry.getValue(), path, data, pathParametersPayload, targets);
            }
        }
    }

    private boolean isPrimitiveArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return false;
        }
        JsonArray array = element.getAsJsonArray();
        return !array.isEmpty() && array.get(0) != null && array.get(0).isJsonPrimitive();
    }

    private RequestTarget.Location targetLocation(ServiceData data, String name, boolean pathParametersPayload) {
        if (pathParametersPayload || !HttpMethod.requiresBody(data.getHttpMethod())) {
            if (OpenApiUtils.getPathVariables(data.getRelativePath()).stream().anyMatch(name::equalsIgnoreCase)) {
                return RequestTarget.Location.PATH;
            }
            if (data.getQueryParams().stream().anyMatch(name::equalsIgnoreCase)) {
                return RequestTarget.Location.QUERY;
            }
        }
        return RequestTarget.Location.BODY;
    }

    private boolean hasExplicitValue(ServiceData data, TargetField target) {
        boolean hasRefData = filesArguments.getRefData(data.getRelativePath()).keySet().stream()
                .map(key -> key.replace('.', '#'))
                .anyMatch(key -> key.equalsIgnoreCase(target.path()));
        if (hasRefData) {
            return true;
        }
        if (target.location() == RequestTarget.Location.PATH && !filesArguments.isNotUrlParam(target.name())) {
            return true;
        }
        return target.location() == RequestTarget.Location.QUERY && filesArguments.getAdditionalQueryParamsForPath(data.getRelativePath()).keySet()
                .stream().anyMatch(key -> key.equalsIgnoreCase(target.name()));
    }

    private boolean hasExplicitHeader(ServiceData data, String name) {
        return filesArguments.getHeaders(data.getContractPath()).keySet().stream()
                .anyMatch(header -> header.equalsIgnoreCase(name));
    }

    private boolean shouldSkipOwnPostIdentifier(ServiceData data, TargetField target) {
        if (data.getHttpMethod() != HttpMethod.POST || target.location() != RequestTarget.Location.BODY) {
            return false;
        }
        Set<String> targetAliases = semanticAliases(target.path(), target.name());
        boolean topLevelIdentifier = !target.path().contains("#") &&
                SIMPLE_IDENTIFIERS.contains(normalize(target.name()));
        return topLevelIdentifier ||
                primaryResourceIdentifierAliases(data.getRelativePath()).stream().anyMatch(targetAliases::contains);
    }

    private String bindingKey(String targetPath, TargetField target) {
        List<String> segments = FIELD_PATH_SPLITTER.splitToList(target.path());
        if (segments.size() > 1) {
            return normalize(segments.get(segments.size() - 2));
        }
        String key = normalize(target.name());
        for (String suffix : List.of("uuids", "guids", "ids", "uuid", "guid", "id")) {
            if (key.endsWith(suffix)) {
                key = key.substring(0, key.length() - suffix.length());
                break;
            }
        }
        for (String qualifier : List.of("external", "internal", "public", "reference", "ref")) {
            if (key.endsWith(qualifier)) {
                key = key.substring(0, key.length() - qualifier.length());
                break;
            }
        }
        return key.isEmpty() ? singular(resourceName(targetPath)) : key;
    }

    private Optional<ResolvedValue> resolve(String targetPath, TargetField target, ResourceInstance boundResource) {
        return resolve(targetPath, target, boundResource, false, null);
    }

    private Optional<ResolvedValue> resolve(String targetPath, TargetField target, ResourceInstance boundResource,
                                            boolean boundOnly) {
        return resolve(targetPath, target, boundResource, boundOnly, null);
    }

    private Optional<ResolvedValue> resolve(String targetPath, TargetField target, ResourceInstance boundResource,
                                            boolean boundOnly, HttpMethod targetMethod) {
        Set<String> targetAliases = targetAliases(targetPath, target);
        boolean destructivePathTarget = target.location() == RequestTarget.Location.PATH &&
                (targetMethod == HttpMethod.DELETE || targetMethod == HttpMethod.PUT || targetMethod == HttpMethod.PATCH);
        Set<String> protectedSelfValues = destructivePathTarget ? selfIdentityValues(targetPath) : Set.of();
        List<ResolvedValue> candidates = resources.stream()
                .filter(ResourceInstance::active)
                .filter(resource -> !destructivePathTarget || !isSelfIdentityGetResource(resource))
                .filter(resource -> !boundOnly || boundResource != null &&
                        resource.sequence() == boundResource.sequence())
                .flatMap(resource -> resource.values().values().stream()
                        .filter(value -> !protectedSelfValues.contains(String.valueOf(value.value())))
                        .filter(value -> value.aliases().stream().anyMatch(targetAliases::contains))
                        .map(value -> new ResolvedValue(resource, value,
                                pathAffinity(resource.sourcePath(), targetPath) * 1_000 +
                                        Math.min(50, matchSpecificity(value, targetAliases)) * 10 +
                                        sourcePriority(resource) + storedValuePriority(value) +
                                        correlationFeedbackScore(resource, targetPath, target))))
                .toList();
        if (candidates.isEmpty() && target.location() == RequestTarget.Location.PATH && !boundOnly) {
            candidates = childPathFallbackCandidates(targetPath, boundResource);
        }
        int bestQuality = candidates.stream()
                .mapToInt(candidate -> candidateQuality(candidate, targetPath, target, targetAliases, boundResource))
                .max().orElse(Integer.MIN_VALUE);
        return candidates.stream()
                .filter(candidate -> candidateQuality(candidate, targetPath, target, targetAliases, boundResource) == bestQuality)
                .max(Comparator.comparingInt(ResolvedValue::score)
                        .thenComparingLong(value -> value.resource().sequence()));
    }

    private boolean isSelfIdentityGetResource(ResourceInstance resource) {
        return resource.sourceMethod() == HttpMethod.GET &&
                SELF_IDENTITY_SEGMENTS.contains(resourceName(resource.sourcePath()));
    }

    private Set<String> selfIdentityValues(String targetPath) {
        return resources.stream()
                .filter(this::isSelfIdentityGetResource)
                .filter(resource -> pathAffinity(resource.sourcePath(), targetPath) >= 2)
                .flatMap(resource -> resource.values().values().stream())
                .map(StoredValue::value)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toSet());
    }

    private List<ResolvedValue> childPathFallbackCandidates(String targetPath, ResourceInstance boundParentResource) {
        List<String> targetSegs = pathSegments(targetPath);
        if (targetSegs.size() < 2) {
            return List.of();
        }
        String rootSegment = targetSegs.getFirst();
        Set<String> boundValues = boundParentResource == null ? Set.of() : boundParentResource.values().values().stream()
                .map(StoredValue::value).map(String::valueOf).collect(Collectors.toSet());
        return resources.stream()
                .filter(ResourceInstance::active)
                .filter(resource -> !resource.fuzzed())
                .filter(resource -> {
                    List<String> srcSegs = pathSegments(resource.sourcePath());
                    return srcSegs.size() >= 2 && srcSegs.getFirst().equalsIgnoreCase(rootSegment) &&
                            srcSegs.stream().anyMatch(this::isTemplateSegment);
                })
                .flatMap(resource -> resource.values().values().stream()
                        .filter(value -> value.aliases().contains("id"))
                        .filter(value -> !boundValues.contains(String.valueOf(value.value())))
                        .map(value -> new ResolvedValue(resource, value,
                                pathAffinity(resource.sourcePath(), targetPath) * 1_000 + sourcePriority(resource))))
                .toList();
    }

    private int candidateQuality(ResolvedValue candidate, String targetPath, TargetField target,
                                 Set<String> targetAliases, ResourceInstance boundResource) {
        int feedbackScore = correlationFeedbackScore(candidate.resource(), targetPath, target);
        int feedbackQuality = feedbackScore < 0 ? feedbackScore - MIN_FEEDBACK_PENALTY : -MIN_FEEDBACK_PENALTY;
        int compatibilityQuality = compatibilityQuality(candidate.storedValue().value(), target);
        int semanticConfidence = semanticConfidence(candidate, targetPath, targetAliases);
        int bindingQuality = boundResource != null && candidate.resource().sequence() == boundResource.sequence() ? 1 : 0;
        return feedbackQuality * 10_000 + compatibilityQuality * 1_000 + semanticConfidence * 100 +
                bindingQuality * 10 + (candidate.resource().fuzzed() ? 0 : 1);
    }

    private int correlationFeedbackScore(ResourceInstance resource, String targetPath, TargetField target) {
        CorrelationFeedbackKey key = new CorrelationFeedbackKey(resource.sequence(), targetPath,
                new RequestTarget(target.location(), target.path()));
        return correlationFeedback.getOrDefault(key, 0);
    }

    private int compatibilityQuality(Object candidateValue, TargetField target) {
        ValueShape candidateShape = valueShape(candidateValue);
        if (target.shape() == candidateShape ||
                (target.shape() == ValueShape.STRING && candidateShape.isString())) {
            return 3;
        }
        if ((target.shape().isNumber() && candidateShape.isNumber()) ||
                (target.shape().isNumericString() && candidateShape.isNumericString())) {
            return 2;
        }
        if (target.shape().isString() && candidateShape.isString()) {
            return 1;
        }
        boolean parameter = target.location() == RequestTarget.Location.PATH ||
                target.location() == RequestTarget.Location.QUERY;
        return parameter && target.shape().isNumeric() && candidateShape.isNumeric() ? 1 : 0;
    }

    private int semanticConfidence(ResolvedValue candidate, String targetPath, Set<String> targetAliases) {
        boolean qualifiedAlias = candidate.storedValue().aliases().stream()
                .filter(alias -> !SIMPLE_IDENTIFIERS.contains(alias))
                .anyMatch(targetAliases::contains);
        if (qualifiedAlias) {
            return 3;
        }
        Set<String> resourceAliases = primaryResourceIdentifierAliases(targetPath);
        return candidate.storedValue().aliases().stream().anyMatch(resourceAliases::contains) ? 2 : 0;
    }

    private Set<String> targetAliases(String targetPath, TargetField target) {
        Set<String> aliases = new LinkedHashSet<>(semanticAliases(target.path(), target.name()));
        if (aliases.stream().anyMatch(SIMPLE_IDENTIFIERS::contains) &&
                (target.location() == RequestTarget.Location.PATH || !target.path().contains("#"))) {
            aliases.addAll(primaryResourceIdentifierAliases(targetPath));
        }
        if (target.location() == RequestTarget.Location.PATH) {
            aliases.addAll(precedingPathSegmentAliases(targetPath, target.name()));
        }
        return Set.copyOf(aliases);
    }

    private Set<String> precedingPathSegmentAliases(String path, String paramName) {
        List<String> segments = pathSegments(path);
        String template = "{" + paramName + "}";
        for (int i = 1; i < segments.size(); i++) {
            if (segments.get(i).equalsIgnoreCase(template) && !isTemplateSegment(segments.get(i - 1))) {
                String resource = normalize(segments.get(i - 1));
                String singular = singular(resource);
                Set<String> aliases = new LinkedHashSet<>();
                for (String suffix : SIMPLE_IDENTIFIERS) {
                    aliases.add(resource + suffix);
                    aliases.add(singular + suffix);
                }
                return aliases;
            }
        }
        return Set.of();
    }

    private Set<String> primaryResourceIdentifierAliases(String path) {
        String resource = resourceName(path);
        String singular = singular(resource);
        Set<String> aliases = new LinkedHashSet<>();
        for (String suffix : SIMPLE_IDENTIFIERS) {
            aliases.add(resource + suffix);
            aliases.add(singular + suffix);
        }
        return aliases;
    }

    private ValueShape pathParameterShape(ServiceData data, String name) {
        for (String payload : List.of(Optional.ofNullable(data.getPathParamsPayload()).orElse(""),
                Optional.ofNullable(data.getPayload()).orElse(""))) {
            if (!JsonUtils.isValidJson(payload)) {
                continue;
            }
            Object value = JsonUtils.getVariableFromJson(payload, name);
            if (!JsonUtils.isNotSet(String.valueOf(value))) {
                return valueShape(value);
            }
        }
        return ValueShape.STRING;
    }

    private ValueShape valueShape(JsonPrimitive value) {
        if (value.isBoolean()) {
            return ValueShape.BOOLEAN;
        }
        if (value.isNumber()) {
            return numberShape(new BigDecimal(value.getAsString()));
        }
        return stringShape(value.getAsString());
    }

    private ValueShape valueShape(Object value) {
        if (value instanceof Boolean) {
            return ValueShape.BOOLEAN;
        }
        if (value instanceof Number number) {
            return numberShape(new BigDecimal(number.toString()));
        }
        return stringShape(String.valueOf(value));
    }

    private ValueShape stringShape(String value) {
        if (UUID_VALUE.matcher(value).matches()) {
            return ValueShape.UUID_STRING;
        }
        try {
            BigDecimal number = new BigDecimal(value);
            return number.stripTrailingZeros().scale() <= 0 ? ValueShape.INTEGER_STRING : ValueShape.DECIMAL_STRING;
        } catch (NumberFormatException _) {
            return ValueShape.STRING;
        }
    }

    private ValueShape numberShape(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0 ? ValueShape.INTEGER_NUMBER : ValueShape.DECIMAL_NUMBER;
    }

    private int matchSpecificity(StoredValue value, Set<String> targetAliases) {
        return value.aliases().stream().filter(targetAliases::contains)
                .mapToInt(String::length).max().orElse(0);
    }

    private int sourcePriority(ResourceInstance resource) {
        int methodPriority = resource.sourceMethod() == HttpMethod.POST ? 3 :
                resource.sourceMethod() == HttpMethod.PUT ? 2 : 1;
        return methodPriority * 2 + (resource.fuzzed() ? 0 : 1);
    }

    private int storedValuePriority(StoredValue value) {
        if (value.sourceLocation().startsWith("request.path.")) {
            return 2;
        }
        return value.sourceLocation().startsWith("response.") ? 1 : 0;
    }

    private int pathAffinity(String sourcePath, String targetPath) {
        List<String> source = pathSegments(sourcePath);
        List<String> target = pathSegments(targetPath);
        int common = 0;
        for (int i = 0; i < Math.min(source.size(), target.size()); i++) {
            String sourceSegment = source.get(i);
            String targetSegment = target.get(i);
            if (isTemplateSegment(sourceSegment) || isTemplateSegment(targetSegment) || sourceSegment.equalsIgnoreCase(targetSegment)) {
                common++;
            } else {
                break;
            }
        }
        return common;
    }

    private void addResource(ServiceData data, Map<String, StoredValue> values) {
        if (values.isEmpty()) {
            return;
        }
        Set<String> resourceIdentifierAliases = resourceIdentifierAliases(data.getRelativePath());
        boolean hasRootSimpleIdentifier = values.entrySet().stream()
                .anyMatch(entry -> isTopLevelResponseKey(entry.getKey()) &&
                        entry.getValue().aliases().stream().anyMatch(SIMPLE_IDENTIFIERS::contains));
        Map<String, StoredValue> enrichedValues = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            Set<String> aliases = new LinkedHashSet<>(value.aliases());
            aliases.add(normalize(key));
            if (hasRootSimpleIdentifier && !isTopLevelResponseKey(key)) {
                aliases.removeAll(SIMPLE_IDENTIFIERS);
            } else if (value.aliases().stream().anyMatch(SIMPLE_IDENTIFIERS::contains)) {
                aliases.addAll(resourceIdentifierAliases);
            }
            enrichedValues.put(key, value.withAliases(aliases));
        });

        resources.add(new ResourceInstance(sequence.incrementAndGet(), data.getRelativePath(), data.getHttpMethod(),
                Collections.unmodifiableMap(enrichedValues), isFuzzedRequest(data), true));
        if (resources.size() > MAX_RESOURCES) {
            ResourceInstance removed = resources.remove(0);
            correlationFeedback.keySet().removeIf(key -> key.resourceSequence() == removed.sequence());
        }
    }

    private boolean isTopLevelResponseKey(String key) {
        String stripped = key.startsWith("$[].") ? key.substring(4) : (key.startsWith("$.") ? key.substring(2) : key);
        return !stripped.contains(".") && !stripped.contains("[]");
    }

    private void addFromLocation(ServiceData data, CatsResponse response, Map<String, StoredValue> pathValues,
                                 List<JsonObject> requestObjects) {
        Map<String, StoredValue> values = new LinkedHashMap<>(pathValues);
        if (requestObjects.size() == 1) {
            collectPrimitiveValues(requestObjects.getFirst(), "$.", "request.body.", values);
        }
        collectResponseHeaders(response, values);
        Optional<StoredValue> locId = locationIdentifier(response);
        if (locId.isPresent()) {
            values.put("id", locId.get());
        } else if (data.getHttpMethod() == HttpMethod.POST && response.getBody() != null) {
            String trimmedBody = response.getBody().trim().replace("\"", "");
            if (isScalarIdentifierBody(trimmedBody)) {
                Set<String> aliases = trimmedBody.matches("^0x[0-9a-fA-F]{40}$")
                        ? Set.of("id", "address", "contractaddress")
                        : Set.of("id");
                values.put("id", new StoredValue(trimmedBody, "response.body.$", aliases));
            }
        }
        addResource(data, values);
    }

    private boolean isScalarIdentifierBody(String value) {
        return !value.isEmpty() && value.length() <= 128 &&
                !value.startsWith("{") && !value.startsWith("[") && !value.startsWith("<") &&
                !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value) && !"null".equalsIgnoreCase(value) &&
                value.chars().noneMatch(Character::isWhitespace);
    }

    private boolean collectResponseHeaders(CatsResponse response, Map<String, StoredValue> values) {
        int initialSize = values.size();
        for (KeyValuePair<String, String> header : Optional.ofNullable(response.getHeaders()).orElse(List.of())) {
            String normalized = normalize(header.getKey());
            if (StringUtils.isBlank(header.getValue()) || !isReusableResponseHeader(normalized)) {
                continue;
            }
            Set<String> aliases = new LinkedHashSet<>(Set.of(normalized));
            if ("etag".equals(normalized)) {
                aliases.add("ifmatch");
                aliases.add("ifnonematch");
            }
            values.putIfAbsent("@header." + normalized, new StoredValue(header.getValue(),
                    "response.header." + header.getKey(), Set.copyOf(aliases)));
        }
        return values.size() > initialSize;
    }

    private boolean isReusableResponseHeader(String normalized) {
        return Set.of("etag", "location", "contentlocation", "operationlocation").contains(normalized) ||
                normalized.contains("version");
    }

    private Optional<StoredValue> locationIdentifier(CatsResponse response) {
        for (String name : List.of("Location", "Content-Location")) {
            KeyValuePair<String, String> locationHeader = response.getHeader(name);
            if (locationHeader == null || StringUtils.isBlank(locationHeader.getValue())) {
                continue;
            }
            String value = lastPathSegment(locationHeader.getValue());
            if (StringUtils.isNotBlank(value)) {
                return Optional.of(new StoredValue(value, "response.header." + name, Set.of("id")));
            }
        }
        return Optional.empty();
    }

    private boolean hasResourceIdentifier(Map<String, StoredValue> values, String path) {
        Set<String> aliases = new LinkedHashSet<>(SIMPLE_IDENTIFIERS);
        aliases.addAll(resourceIdentifierAliases(path));
        return values.values().stream()
                .filter(value -> value.sourceLocation().startsWith("response.body."))
                .anyMatch(value -> value.aliases().stream().anyMatch(aliases::contains));
    }

    private void markDeleted(ServiceData data, String actualUrl) {
        Map<String, StoredValue> deletedValues = new LinkedHashMap<>(
                extractPathValues(data.getRelativePath(), actualUrl));
        if (deletedValues.isEmpty()) {
            extractIdentifierQueryValues(data, actualUrl).forEach(deletedValues::putIfAbsent);
        }
        if (deletedValues.isEmpty()) {
            return;
        }
        for (int i = 0; i < resources.size(); i++) {
            ResourceInstance resource = resources.get(i);
            boolean matches = deletedValues.entrySet().stream().allMatch(deleted -> {
                Set<String> deletedAliases = deletedAliases(data.getRelativePath(), deleted.getKey(), deleted.getValue());
                return resource.values().values().stream()
                        .anyMatch(stored -> stored.aliases().stream().anyMatch(deletedAliases::contains) &&
                                sameValue(stored.value(), deleted.getValue().value()));
            });
            if (matches) {
                resources.set(i, resource.deleted());
                correlationFeedback.keySet().removeIf(key -> key.resourceSequence() == resource.sequence());
            }
        }
        Set<String> deletedPrimitiveValues = deletedValues.values().stream()
                .map(StoredValue::value).map(String::valueOf).collect(Collectors.toSet());
        successfulRequestBaselines.entrySet().removeIf(entry ->
                containsPrimitiveValue(entry.getValue(), deletedPrimitiveValues));
    }

    private Set<String> deletedAliases(String path, String deletedKey, StoredValue deletedValue) {
        Set<String> aliases = new LinkedHashSet<>(deletedValue.aliases());
        String normalizedKey = normalize(deletedKey);
        aliases.add(normalizedKey);
        if (SIMPLE_IDENTIFIERS.contains(normalizedKey)) {
            Set<String> qualified = new LinkedHashSet<>(primaryResourceIdentifierAliases(path));
            qualified.addAll(precedingPathSegmentAliases(path, deletedKey));
            if (!qualified.isEmpty()) {
                aliases.removeAll(SIMPLE_IDENTIFIERS);
                aliases.addAll(qualified);
            }
        }
        return aliases;
    }

    private Map<String, StoredValue> extractIdentifierQueryValues(ServiceData data, String actualUrl) {
        if (StringUtils.isBlank(actualUrl)) {
            return Map.of();
        }
        HttpUrl url;
        try {
            url = HttpUrl.get(actualUrl);
        } catch (IllegalArgumentException _) {
            return Map.of();
        }
        Map<String, StoredValue> values = new LinkedHashMap<>();
        Optional.ofNullable(data.getQueryParams()).orElse(Set.of()).stream()
                .filter(this::isIdentifierName)
                .forEach(name -> {
                    List<String> queryValues = url.queryParameterValues(name);
                    if (!queryValues.isEmpty()) {
                        String value = queryValues.getLast();
                        values.put(name, new StoredValue(value, "request.query." + name, Set.of(normalize(name))));
                    }
                });
        return values;
    }

    private boolean containsPrimitiveValue(String payload, Set<String> values) {
        if (!JsonUtils.isValidJson(payload)) {
            return false;
        }
        try {
            return containsPrimitiveValue(JsonUtils.parseAsJsonElement(payload), null, values);
        } catch (RuntimeException _) {
            return false;
        }
    }

    private boolean containsPrimitiveValue(JsonElement element, String fieldName, Set<String> values) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonPrimitive()) {
            return (fieldName == null || isIdentifierName(fieldName)) &&
                    values.stream().anyMatch(deleted -> sameValue(deleted, primitiveValue(element.getAsJsonPrimitive())));
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().asList().stream()
                    .anyMatch(item -> containsPrimitiveValue(item, fieldName, values));
        }
        return element.getAsJsonObject().entrySet().stream()
                .anyMatch(entry -> containsPrimitiveValue(entry.getValue(), entry.getKey(), values));
    }

    private Map<String, StoredValue> extractPathValues(String templatePath, String actualUrl) {
        if (StringUtils.isBlank(templatePath) || StringUtils.isBlank(actualUrl)) {
            return Map.of();
        }
        List<String> templateSegments = pathSegments(templatePath);
        List<String> actualSegments;
        try {
            actualSegments = HttpUrl.get(actualUrl).pathSegments().stream()
                    .filter(StringUtils::isNotBlank).toList();
        } catch (IllegalArgumentException _) {
            return Map.of();
        }
        if (actualSegments.size() < templateSegments.size()) {
            return Map.of();
        }
        int offset = actualSegments.size() - templateSegments.size();
        Map<String, StoredValue> values = new LinkedHashMap<>();
        for (int i = 0; i < templateSegments.size(); i++) {
            String segment = templateSegments.get(i);
            if (isTemplateSegment(segment)) {
                String name = segment.substring(1, segment.length() - 1);
                String value = actualSegments.get(offset + i);
                values.put(name, new StoredValue(value, "request.path." + name, Set.of(normalize(name))));
            }
        }
        return values;
    }

    private boolean alignedObjectPayloads(String requestPayload, String responseBody,
                                          int requestObjectCount, int responseObjectCount) {
        if (requestObjectCount == 0 || requestObjectCount != responseObjectCount ||
                !JsonUtils.isValidJson(requestPayload) || !JsonUtils.isValidJson(responseBody)) {
            return false;
        }
        try {
            JsonElement requestRoot = JsonUtils.parseAsJsonElement(requestPayload);
            JsonElement responseRoot = JsonUtils.parseAsJsonElement(responseBody);
            if (requestRoot.isJsonObject() || responseRoot.isJsonObject()) {
                return requestRoot.isJsonObject() && responseRoot.isJsonObject();
            }
            return requestRoot.isJsonArray() && responseRoot.isJsonArray() &&
                    containsOnlyObservedObjects(requestRoot.getAsJsonArray(), requestObjectCount) &&
                    containsOnlyObservedObjects(responseRoot.getAsJsonArray(), responseObjectCount);
        } catch (RuntimeException _) {
            return false;
        }
    }

    private boolean containsOnlyObservedObjects(JsonArray array, int expectedObjects) {
        int limit = Math.min(array.size(), MAX_COLLECTION_ITEMS);
        if (limit != expectedObjects) {
            return false;
        }
        for (int i = 0; i < limit; i++) {
            if (!array.get(i).isJsonObject()) {
                return false;
            }
        }
        return true;
    }

    private List<JsonObject> requestObjects(String payload) {
        if (!JsonUtils.isValidJson(payload)) {
            return List.of();
        }
        JsonElement root;
        try {
            root = JsonUtils.parseAsJsonElement(payload);
        } catch (RuntimeException _) {
            return List.of();
        }
        if (root.isJsonObject()) {
            return List.of(root.getAsJsonObject());
        }
        if (!root.isJsonArray()) {
            return List.of();
        }
        return root.getAsJsonArray().asList().stream()
                .filter(JsonElement::isJsonObject)
                .limit(MAX_COLLECTION_ITEMS)
                .map(JsonElement::getAsJsonObject)
                .toList();
    }

    private List<ResponseObject> responseObjects(String body, boolean collectionOnly) {
        if (!JsonUtils.isValidJson(body)) {
            return List.of();
        }
        JsonElement root;
        try {
            root = JsonUtils.parseAsJsonElement(body);
        } catch (RuntimeException _) {
            return List.of();
        }
        List<ResponseObject> objects = new ArrayList<>();
        if (root.isJsonArray()) {
            addObjectElements(root.getAsJsonArray(), "$[].", objects);
        } else if (root.isJsonObject()) {
            if (collectionOnly) {
                collectNestedArrayObjects(root.getAsJsonObject(), "$.", objects);
                if (objects.isEmpty()) {
                    objects.add(new ResponseObject(root.getAsJsonObject(), "$."));
                }
            } else {
                objects.add(new ResponseObject(root.getAsJsonObject(), "$."));
                if (!containsIdentifierOutsideArrays(root.getAsJsonObject())) {
                    collectNestedArrayObjects(root.getAsJsonObject(), "$.", objects);
                }
            }
        }
        return objects;
    }

    private boolean isCollectionPath(String path) {
        List<String> segments = pathSegments(path);
        return !segments.isEmpty() && !isTemplateSegment(segments.getLast());
    }

    private boolean isCollectionResponse(String body) {
        if (!JsonUtils.isValidJson(body)) {
            return false;
        }
        JsonElement root = JsonUtils.parseAsJsonElement(body);
        return root.isJsonArray() || (root.isJsonObject() && containsArray(root.getAsJsonObject()));
    }

    private boolean containsArray(JsonObject object) {
        return object.entrySet().stream().anyMatch(entry -> entry.getValue().isJsonArray() ||
                (entry.getValue().isJsonObject() && containsArray(entry.getValue().getAsJsonObject())));
    }

    private void collectNestedArrayObjects(JsonObject object, String prefix, List<ResponseObject> objects) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (objects.size() >= MAX_COLLECTION_ITEMS) {
                return;
            }
            String location = prefix + entry.getKey();
            if (entry.getValue().isJsonArray()) {
                addObjectElements(entry.getValue().getAsJsonArray(), location + "[].", objects);
            } else if (entry.getValue().isJsonObject()) {
                collectNestedArrayObjects(entry.getValue().getAsJsonObject(), location + ".", objects);
            }
        }
    }

    private boolean containsIdentifierOutsideArrays(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue().isJsonPrimitive() && isIdentifierName(entry.getKey())) {
                return true;
            }
            if (entry.getValue().isJsonObject() && containsIdentifierOutsideArrays(entry.getValue().getAsJsonObject())) {
                return true;
            }
        }
        return false;
    }

    private void addObjectElements(JsonArray array, String sourcePrefix, List<ResponseObject> objects) {
        for (JsonElement element : array) {
            if (element.isJsonObject() && objects.size() < MAX_COLLECTION_ITEMS) {
                objects.add(new ResponseObject(element.getAsJsonObject(), sourcePrefix));
            }
        }
    }

    private void collectPrimitiveValues(JsonObject object, String prefix, String sourcePrefix,
                                        Map<String, StoredValue> values) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String location = prefix + entry.getKey();
            if (entry.getValue().isJsonPrimitive()) {
                JsonPrimitive primitive = entry.getValue().getAsJsonPrimitive();
                Object value = primitiveValue(primitive);
                values.putIfAbsent(location, new StoredValue(value, sourcePrefix + location,
                        semanticAliases(location, entry.getKey())));
            } else if (entry.getValue().isJsonObject()) {
                collectPrimitiveValues(entry.getValue().getAsJsonObject(), location + ".", sourcePrefix, values);
            } else if (entry.getValue().isJsonArray()) {
                collectFirstArrayElementValues(entry.getValue().getAsJsonArray(), entry.getKey(), location, sourcePrefix, values);
            }
        }
    }

    private void collectFirstArrayElementValues(JsonArray array, String key, String location, String sourcePrefix,
                                                Map<String, StoredValue> values) {
        for (JsonElement item : array) {
            if (item != null && (item.isJsonObject() || item.isJsonPrimitive())) {
                if (item.isJsonObject()) {
                    collectPrimitiveValues(item.getAsJsonObject(), location + "[].", sourcePrefix, values);
                } else {
                    Object value = primitiveValue(item.getAsJsonPrimitive());
                    Set<String> aliases = new LinkedHashSet<>(semanticAliases(location, key));
                    aliases.add(singular(normalize(key)));
                    values.putIfAbsent(location + "[]", new StoredValue(value, sourcePrefix + location + "[]",
                            Set.copyOf(aliases)));
                }
                break;
            }
        }
    }

    private Object primitiveValue(JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return new BigDecimal(primitive.getAsString());
        }
        return primitive.getAsString();
    }

    private Set<String> semanticAliases(String path, String name) {
        Set<String> aliases = new LinkedHashSet<>();
        addWithSingularIdForms(normalize(name), aliases);
        addWordBoundarySuffixAliases(name, aliases);
        List<String> segments = SEMANTIC_PATH_SPLITTER.splitToList(path.replace("$", ""));
        StringBuilder suffix = new StringBuilder();
        for (int i = segments.size() - 1; i >= 0; i--) {
            suffix.insert(0, segments.get(i));
            addWithSingularIdForms(normalize(suffix.toString()), aliases);
        }
        return Set.copyOf(aliases);
    }

    private void addWordBoundarySuffixAliases(String rawName, Set<String> aliases) {
        if (StringUtils.isBlank(rawName)) {
            return;
        }
        List<String> words = Splitter.on(WORD_BOUNDARY_SPLITTER).omitEmptyStrings().splitToList(rawName.trim());
        if (words.size() >= 3) {
            String lastWord = normalize(words.getLast());
            if (SIMPLE_IDENTIFIERS.contains(lastWord) || SIMPLE_PLURAL_IDENTIFIERS.contains(lastWord)) {
                String twoWordSuffix = normalize(words.get(words.size() - 2)) + lastWord;
                addWithSingularIdForms(twoWordSuffix, aliases);
            }
        }
        if (words.size() >= 2) {
            String lastWord = normalize(words.getLast());
            String fullNormalized = normalize(rawName);
            if ("address".equals(lastWord) && !fullNormalized.equals("contractaddress") && !fullNormalized.contains("email")) {
                aliases.add("address");
            } else if ("name".equals(lastWord)) {
                aliases.add("name");
            }
        }
    }

    private void addWithSingularIdForms(String normalizedAlias, Set<String> aliases) {
        if (normalizedAlias.isEmpty()) {
            return;
        }
        aliases.add(normalizedAlias);
        for (String pluralSuffix : SIMPLE_PLURAL_IDENTIFIERS) {
            if (normalizedAlias.endsWith(pluralSuffix)) {
                String singularSuffix = pluralSuffix.substring(0, pluralSuffix.length() - 1);
                String prefix = normalizedAlias.substring(0, normalizedAlias.length() - pluralSuffix.length());
                if (prefix.isEmpty()) {
                    aliases.add(singularSuffix);
                } else {
                    aliases.add(prefix + singularSuffix);
                    aliases.add(singular(prefix) + singularSuffix);
                }
                return;
            }
        }
        for (String singularSuffix : SIMPLE_IDENTIFIERS) {
            if (normalizedAlias.endsWith(singularSuffix) && normalizedAlias.length() > singularSuffix.length()) {
                String prefix = normalizedAlias.substring(0, normalizedAlias.length() - singularSuffix.length());
                aliases.add(singular(prefix) + singularSuffix);
                return;
            }
        }
    }

    private boolean hasSiblingPostValue(ServiceData data, String fieldName) {
        if (data == null || data.getHttpMethod() != HttpMethod.POST) {
            return false;
        }
        List<String> targetSegments = pathSegments(data.getRelativePath());
        if (targetSegments.size() < 2) {
            return false;
        }
        String normalizedField = normalize(fieldName);
        return resources.stream()
                .filter(ResourceInstance::active)
                .filter(resource -> !resource.fuzzed() && resource.sourceMethod() == HttpMethod.POST)
                .filter(resource -> sameParentPath(pathSegments(resource.sourcePath()), targetSegments))
                .flatMap(resource -> resource.values().values().stream())
                .filter(value -> value.sourceLocation().startsWith("request.body."))
                .anyMatch(value -> value.aliases().contains(normalizedField));
    }

    private boolean sameParentPath(List<String> sourceSegments, List<String> targetSegments) {
        if (sourceSegments.size() != targetSegments.size() || sourceSegments.size() < 2) {
            return false;
        }
        for (int i = 0; i < sourceSegments.size() - 1; i++) {
            if (!sourceSegments.get(i).equalsIgnoreCase(targetSegments.get(i))) {
                return false;
            }
        }
        return !sourceSegments.getLast().equalsIgnoreCase(targetSegments.getLast());
    }

    private boolean isIdentifierName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return SIMPLE_IDENTIFIERS.contains(lower) || SIMPLE_PLURAL_IDENTIFIERS.contains(lower) ||
                "username".equals(lower) || "token".equals(lower) ||
                SEPARATED_ID.matcher(name).matches() ||
                CAMEL_IDENTIFIER.matcher(name).matches();
    }

    private boolean wasModifiedByFuzzer(ServiceData data, String path, boolean pathParametersPayload) {
        if (pathParametersPayload || StringUtils.isBlank(data.getOriginalPayload()) ||
                !JsonUtils.isValidJson(data.getOriginalPayload())) {
            return false;
        }
        return !valuesAtPath(data.getOriginalPayload(), path).equals(valuesAtPath(data.getPayload(), path));
    }

    private boolean isRequestBodyFuzzed(ServiceData data) {
        boolean hasDeclaredBodyMutation = data.getAllMutationTargets().stream().anyMatch(target ->
                target.location() == RequestTarget.Location.BODY ||
                        target.location() == RequestTarget.Location.REQUEST_BODY);
        if (hasDeclaredBodyMutation || StringUtils.isBlank(data.getOriginalPayload())) {
            return hasDeclaredBodyMutation;
        }
        if (JsonUtils.isValidJson(data.getOriginalPayload()) && JsonUtils.isValidJson(data.getPayload())) {
            return !JsonUtils.parseAsJsonElement(data.getOriginalPayload())
                    .equals(JsonUtils.parseAsJsonElement(data.getPayload()));
        }
        return !data.getOriginalPayload().equals(data.getPayload());
    }

    private boolean isFuzzedRequest(ServiceData data) {
        boolean hasDeclaredMutation = data.hasDeclaredMutation();
        if (hasDeclaredMutation || StringUtils.isBlank(data.getOriginalPayload())) {
            return hasDeclaredMutation;
        }
        if (JsonUtils.isValidJson(data.getOriginalPayload()) && JsonUtils.isValidJson(data.getPayload())) {
            return !JsonUtils.parseAsJsonElement(data.getOriginalPayload())
                    .equals(JsonUtils.parseAsJsonElement(data.getPayload()));
        }
        return !data.getOriginalPayload().equals(data.getPayload());
    }

    private List<JsonElement> valuesAtPath(String json, String path) {
        JsonElement root;
        try {
            root = JsonUtils.parseAsJsonElement(json);
        } catch (RuntimeException _) {
            return List.of();
        }

        List<JsonElement> current = List.of(root);
        for (String segment : FIELD_PATH_SPLITTER.split(path)) {
            List<JsonElement> next = new ArrayList<>();
            current.forEach(element -> collectValuesForSegment(element, segment, next));
            current = next;
        }
        return current;
    }

    private void collectValuesForSegment(JsonElement element, String segment, List<JsonElement> values) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> collectValuesForSegment(item, segment, values));
        } else if (element.isJsonObject() && element.getAsJsonObject().has(segment)) {
            values.add(element.getAsJsonObject().get(segment));
        }
    }

    private String resourceName(String path) {
        List<String> segments = pathSegments(path);
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (!isTemplateSegment(segments.get(i))) {
                return normalize(segments.get(i));
            }
        }
        return "resource";
    }

    private String singular(String resource) {
        String irregular = IRREGULAR_SINGULARS.get(resource);
        if (irregular != null) {
            return irregular;
        }
        if (resource.endsWith("ais") && resource.length() > 3) {
            return resource.substring(0, resource.length() - 3) + "al";
        }
        if (resource.endsWith("ies") && resource.length() > 3) {
            return resource.substring(0, resource.length() - 3) + "y";
        }
        if ((resource.endsWith("sses") || resource.endsWith("xes") || resource.endsWith("zes") ||
                resource.endsWith("ches") || resource.endsWith("shes")) && resource.length() > 2) {
            return resource.substring(0, resource.length() - 2);
        }
        if (resource.endsWith("s") && !resource.endsWith("ss") && resource.length() > 1) {
            return resource.substring(0, resource.length() - 1);
        }
        return resource;
    }

    private Set<String> resourceIdentifierAliases(String path) {
        List<String> segments = pathSegments(path);
        int lastResourceIndex = -1;
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (!isTemplateSegment(segments.get(i))) {
                lastResourceIndex = i;
                break;
            }
        }
        if (lastResourceIndex < 0) {
            return Set.of();
        }
        int startIndex = lastResourceIndex;
        while (startIndex > 0 && !isTemplateSegment(segments.get(startIndex - 1))) {
            startIndex--;
        }
        Set<String> aliases = new LinkedHashSet<>();
        segments.subList(startIndex, lastResourceIndex + 1).stream()
                .map(this::normalize)
                .filter(StringUtils::isNotBlank)
                .forEach(resource -> {
                    aliases.add(resource + "id");
                    aliases.add(singular(resource) + "id");
                });
        return aliases;
    }

    private List<String> pathSegments(String path) {
        if (path == null) {
            return List.of();
        }
        return List.of(path.split("/"))
                .stream().filter(StringUtils::isNotBlank).toList();
    }

    private boolean isTemplateSegment(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    private String lastPathSegment(String location) {
        try {
            List<String> segments = HttpUrl.get(location).pathSegments().stream()
                    .filter(StringUtils::isNotBlank).toList();
            return segments.isEmpty() ? "" : segments.getLast();
        } catch (IllegalArgumentException _) {
            int queryIndex = location.indexOf('?');
            int fragmentIndex = location.indexOf('#');
            int suffixIndex = queryIndex < 0 ? fragmentIndex :
                    fragmentIndex < 0 ? queryIndex : Math.min(queryIndex, fragmentIndex);
            String path = suffixIndex < 0 ? location : location.substring(0, suffixIndex);
            path = StringUtils.stripEnd(path, "/");
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    private String normalize(String value) {
        return NON_ALPHANUMERIC.matcher(Optional.ofNullable(value).orElse(""))
                .replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** Result of applying runtime resource correlations to request data. */
    public record ResolvedRequest(String payload, String pathParamsPayload, List<ResourceCorrelation> correlations) {
        public ResolvedRequest {
            correlations = List.copyOf(correlations);
        }
    }

    public record ResolvedHeaders(List<KeyValuePair<String, Object>> headers, List<ResourceCorrelation> correlations) {
        public ResolvedHeaders {
            headers = List.copyOf(headers);
            correlations = List.copyOf(correlations);
        }
    }

    /** Why an identifier parameter is considered unresolved. */
    public enum UnresolvedReason {
        /** No matching value was captured from earlier successful responses. */
        NO_VALUE_CAPTURED,
        /** Values reused from earlier responses always returned 404 or 410. */
        CAPTURED_VALUES_NOT_FOUND
    }

    /**
     * Aggregated outcome of the unmodified requests sent to an operation.
     *
     * @param path        contract path
     * @param method      HTTP method
     * @param requests    number of unmodified requests
     * @param successes   number of 2xx responses
     * @param statusCodes response code counts
     */
    public record OperationOutcome(String path, HttpMethod method, int requests, int successes,
                                   Map<Integer, Integer> statusCodes) {
        public OperationOutcome {
            statusCodes = Collections.unmodifiableMap(new LinkedHashMap<>(statusCodes));
        }
    }

    /**
     * An identifier parameter that could not be resolved at runtime.
     *
     * @param target   request location and name of the parameter
     * @param reason   why the parameter is considered unresolved
     * @param producer outcome of the likely producer operation (POST on the parent collection), or {@code null}
     */
    public record UnresolvedParameter(RequestTarget target, UnresolvedReason reason, OperationOutcome producer) {
    }

    /**
     * An operation that never succeeded and depends on unresolved identifier parameters.
     *
     * @param outcome    outcome of the operation
     * @param parameters unresolved identifier parameters
     */
    public record UnresolvedOperation(OperationOutcome outcome, List<UnresolvedParameter> parameters) {
    }

    private record OperationKey(String path, HttpMethod method) {
    }

    private static final class ParameterDiagnostics {
        private int resolved;
        private int unresolved;
        private int resolvedNotFound;

        Optional<UnresolvedReason> reason() {
            if (resolved == 0 && unresolved > 0) {
                return Optional.of(UnresolvedReason.NO_VALUE_CAPTURED);
            }
            if (resolved > 0 && resolvedNotFound == resolved) {
                return Optional.of(UnresolvedReason.CAPTURED_VALUES_NOT_FOUND);
            }
            return Optional.empty();
        }
    }

    private static final class OperationDiagnostics {
        private final Map<Integer, Integer> statusCodes = new LinkedHashMap<>();
        private final Map<RequestTarget, ParameterDiagnostics> parameters = new LinkedHashMap<>();
        private int requests;
        private int successes;

        void register(int responseCode) {
            requests++;
            if (ResponseCodeFamily.is2xxCode(responseCode)) {
                successes++;
            }
            statusCodes.merge(responseCode, 1, Integer::sum);
        }

        void recordParameter(RequestTarget target, boolean resolved, int responseCode) {
            ParameterDiagnostics stats = parameters.computeIfAbsent(target, _ -> new ParameterDiagnostics());
            if (!resolved) {
                stats.unresolved++;
                return;
            }
            stats.resolved++;
            if (responseCode == 404 || responseCode == 410) {
                stats.resolvedNotFound++;
            }
        }

        boolean onlyAuthFailures() {
            return statusCodes.keySet().stream().allMatch(code -> code == 401 || code == 403);
        }

        OperationOutcome outcome(OperationKey key) {
            return new OperationOutcome(key.path(), key.method(), requests, successes, statusCodes);
        }
    }

    private record TargetField(String path, String name, RequestTarget.Location location, ValueShape shape) {
    }

    private enum ValueShape {
        BOOLEAN,
        INTEGER_NUMBER,
        DECIMAL_NUMBER,
        UUID_STRING,
        INTEGER_STRING,
        DECIMAL_STRING,
        STRING;

        boolean isString() {
            return this == UUID_STRING || this == INTEGER_STRING || this == DECIMAL_STRING || this == STRING;
        }

        boolean isNumber() {
            return this == INTEGER_NUMBER || this == DECIMAL_NUMBER;
        }

        boolean isNumericString() {
            return this == INTEGER_STRING || this == DECIMAL_STRING;
        }

        boolean isNumeric() {
            return isNumber() || isNumericString();
        }
    }

    private record SuccessfulBaselineKey(String path, HttpMethod method, String contentType, String variant) {
    }

    private record CorrelationFeedbackKey(long resourceSequence, String targetPath, RequestTarget target) {
    }

    private record ResolvedValue(ResourceInstance resource, StoredValue storedValue, int score) {
        Object value() {
            return storedValue.value();
        }
    }

    private record StoredValue(Object value, String sourceLocation, Set<String> aliases) {
        StoredValue withAliases(Set<String> newAliases) {
            return new StoredValue(value, sourceLocation, Set.copyOf(newAliases));
        }
    }

    private record ResponseObject(JsonObject value, String sourcePrefix) {
    }

    private record ResourceInstance(long sequence, String sourcePath, HttpMethod sourceMethod,
                                    Map<String, StoredValue> values, boolean fuzzed, boolean active) {
        ResourceInstance deleted() {
            return new ResourceInstance(sequence, sourcePath, sourceMethod, values, fuzzed, false);
        }
    }
}
