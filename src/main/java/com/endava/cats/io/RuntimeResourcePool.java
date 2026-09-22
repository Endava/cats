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
    private static final int MAX_FEEDBACK_REWARD = 500;
    private static final int MIN_FEEDBACK_PENALTY = -5_000;
    private static final int SUCCESS_REWARD = 100;
    private static final int NOT_FOUND_PENALTY = -1_000;
    private static final int CONFLICT_PENALTY = -200;
    private static final Pattern SEPARATED_ID = Pattern.compile("(?i).*(?:_|-)(?:id|uuid|guid)$");
    private static final Pattern UUID_VALUE = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]");
    private static final Splitter SEMANTIC_PATH_SPLITTER = Splitter.on(Pattern.compile("[.#]")).omitEmptyStrings();
    private static final Splitter FIELD_PATH_SPLITTER = Splitter.on('#').omitEmptyStrings();
    private static final Set<String> SIMPLE_IDENTIFIERS = Set.of("id", "uuid", "guid");
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
        String enrichedPayload = enrichJson(processedPayload, data, correlations, correlationSources, requestBindings, false);
        String enrichedPathParams = enrichJson(data.getPathParamsPayload(), data, correlations, correlationSources,
                requestBindings, true);
        ResolvedRequest resolvedRequest = new ResolvedRequest(enrichedPayload, enrichedPathParams, List.copyOf(correlations));
        if (!correlationSources.isEmpty()) {
            pendingCorrelationSources.put(resolvedRequest, Map.copyOf(correlationSources));
            while (pendingCorrelationSources.size() > MAX_CORRELATION_FEEDBACK) {
                pendingCorrelationSources.remove(pendingCorrelationSources.keySet().iterator().next());
            }
        }
        return resolvedRequest;
    }

    synchronized Map<String, String> resolvePathParameters(ServiceData data) {
        if (!processingArguments.isReuseSuccessfulResources() || data.getHttpMethod() != HttpMethod.DELETE ||
                !data.isReplaceRefData() || !data.isReplaceUrlParams() || resources.isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolvedParameters = new LinkedHashMap<>();
        Map<String, ResourceInstance> requestBindings = new LinkedHashMap<>();
        for (String name : OpenApiUtils.getPathVariables(data.getRelativePath())) {
            TargetField target = new TargetField(name, name, RequestTarget.Location.PATH,
                    pathParameterShape(data, name));
            if (data.isFuzzedField(name, RequestTarget.Location.PATH) || hasExplicitValue(data, target)) {
                continue;
            }
            String bindingKey = bindingKey(data.getRelativePath(), target);
            resolve(data.getRelativePath(), target, requestBindings.get(bindingKey)).ifPresent(value -> {
                resolvedParameters.put(name, String.valueOf(value.value()));
                requestBindings.put(bindingKey, value.resource());
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
        ResourceInstance pathResource = resolvedPathResource(resolvedRequest).orElse(null);
        if (resolvedRequest != null && !OpenApiUtils.getPathVariables(data.getRelativePath()).isEmpty() &&
                pathResource == null) {
            return new ResolvedHeaders(List.copyOf(headers), List.of());
        }
        headers.stream().map(KeyValuePair::getKey).distinct().forEach(name -> {
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
        });
        return new ResolvedHeaders(List.copyOf(enriched), List.copyOf(correlations));
    }

    private Optional<ResourceInstance> resolvedPathResource(ResolvedRequest resolvedRequest) {
        Map<ResourceCorrelation, Long> sources = pendingCorrelationSources.get(resolvedRequest);
        if (sources == null) {
            return Optional.empty();
        }
        Set<Long> pathResourceSequences = new LinkedHashSet<>();
        sources.forEach((correlation, resourceSequence) -> {
            if (correlation.getTarget().location() == RequestTarget.Location.PATH) {
                pathResourceSequences.add(resourceSequence);
            }
        });
        if (pathResourceSequences.size() != 1) {
            return Optional.empty();
        }
        long resourceSequence = pathResourceSequences.iterator().next();
        return resources.stream().filter(ResourceInstance::active)
                .filter(resource -> resource.sequence() == resourceSequence).findFirst();
    }

    synchronized String applySuccessfulRequestBaseline(ServiceData data) {
        String currentPayload = data.getPayload();
        if (!processingArguments.isReuseSuccessfulResources() || !data.isReplaceRefData() || !data.isValidJson() ||
                !data.isJsonContentType() || !HttpMethod.requiresBody(data.getHttpMethod()) ||
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
        updateCorrelationFeedback(data, response, resolvedRequest);
        if (!ResponseCodeFamily.is2xxCode(response.getResponseCode())) {
            return;
        }
        storeSuccessfulRequestBaseline(data, request);
        if (data.getHttpMethod() == HttpMethod.DELETE) {
            markDeleted(data, request.getUrl());
            return;
        }

        if (data.getHttpMethod() != HttpMethod.POST && data.getHttpMethod() != HttpMethod.PUT &&
                data.getHttpMethod() != HttpMethod.PATCH && data.getHttpMethod() != HttpMethod.GET &&
                data.getHttpMethod() != HttpMethod.HEAD) {
            return;
        }

        Map<String, StoredValue> pathValues = extractPathValues(data.getRelativePath(), request.getUrl());
        List<JsonObject> requestObjects = HttpMethod.requiresBody(data.getHttpMethod()) && !isRequestBodyFuzzed(data)
                ? requestObjects(request.getPayload()) : List.of();
        if (response.isBodyTruncated()) {
            addFromLocation(data, response, pathValues, requestObjects);
            return;
        }
        boolean collectionGet = data.getHttpMethod() == HttpMethod.GET;
        if (collectionGet && (!isCollectionPath(data.getRelativePath()) || !isCollectionResponse(response.getBody()))) {
            boolean reusableHeaders = collectResponseHeaders(response, pathValues);
            boolean reusableItemPath = !isCollectionPath(data.getRelativePath()) && !pathValues.isEmpty() &&
                    OpenApiUtils.getPathVariables(data.getRelativePath()).stream()
                            .noneMatch(name -> data.isFuzzedField(name, RequestTarget.Location.PATH) ||
                                    wasModifiedByFuzzer(data, name, false));
            if (reusableHeaders || reusableItemPath) {
                addResource(data, pathValues);
            }
            return;
        }
        List<ResponseObject> responseObjects = responseObjects(response.getBody(), collectionGet);

        if (responseObjects.isEmpty()) {
            addFromLocation(data, response, pathValues, requestObjects);
            return;
        }

        boolean singleResponseObject = responseObjects.size() == 1;
        Optional<StoredValue> locationIdentifier = singleResponseObject ? locationIdentifier(response) : Optional.empty();
        boolean alignedRequestObjects = alignedObjectPayloads(request.getPayload(), response.getBody(),
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
            if (!hasResourceIdentifier(values, data.getRelativePath())) {
                locationIdentifier.ifPresent(value -> values.put("id", value));
            }
            addResource(data, values);
        }
        if (!singleResponseObject) {
            Map<String, StoredValue> headerValues = new LinkedHashMap<>(pathValues);
            if (collectResponseHeaders(response, headerValues)) {
                addResource(data, headerValues);
            }
        }
    }

    /** Clears all values captured by the current run. */
    public synchronized void clear() {
        resources.clear();
        successfulRequestBaselines.clear();
        correlationFeedback.clear();
        pendingCorrelationSources.clear();
        sequence.set(0);
    }

    synchronized void discard(ResolvedRequest resolvedRequest) {
        pendingCorrelationSources.remove(resolvedRequest);
    }

    private void storeSuccessfulRequestBaseline(ServiceData data, CatsRequest request) {
        if (isRequestBodyFuzzed(data) || !data.isReplaceRefData() || !data.isValidJson() || !data.isJsonContentType() ||
                !HttpMethod.requiresBody(data.getHttpMethod()) || !JsonUtils.isValidJson(request.getPayload())) {
            return;
        }
        successfulBaselineKey(data).ifPresent(key -> {
            successfulRequestBaselines.put(key, JsonUtils.parseAsJsonElement(request.getPayload()).toString());
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
            JsonArray originalArray = original.getAsJsonArray();
            JsonArray currentArray = current.getAsJsonArray();
            JsonArray successfulArray = successful.getAsJsonArray();
            if (originalArray.size() == currentArray.size() && currentArray.size() == successfulArray.size()) {
                JsonArray result = new JsonArray();
                for (int i = 0; i < currentArray.size(); i++) {
                    result.add(mergeSuccessfulBaseline(originalArray.get(i), currentArray.get(i), successfulArray.get(i)));
                }
                return result;
            }
        }
        if (current.isJsonPrimitive() && successful.isJsonPrimitive()) {
            Object merged = FuzzingStrategy.mergeFuzzing(primitiveValue(current.getAsJsonPrimitive()),
                    primitiveValue(successful.getAsJsonPrimitive()));
            return JsonUtils.parseAsJsonElement(JsonUtils.serialize(merged));
        }
        return current.deepCopy();
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
        if (!JsonUtils.isValidJson(json)) {
            return json;
        }

        String result = json;
        for (TargetField target : reusableTargets(json, data, pathParametersPayload)) {
            if ((target.location() == RequestTarget.Location.PATH && !data.isReplaceUrlParams()) ||
                    hasExplicitValue(data, target) || shouldSkipOwnPostIdentifier(data, target)) {
                continue;
            }

            String bindingKey = bindingKey(data.getRelativePath(), target);
            Optional<ResolvedValue> resolved = resolve(data.getRelativePath(), target, requestBindings.get(bindingKey));
            if (resolved.isEmpty()) {
                continue;
            }

            String before = result;
            ResolvedValue value = resolved.get();
            boolean mergeFuzzing = data.isFuzzedField(target.path(), target.location()) ||
                    wasModifiedByFuzzer(data, target.path(), pathParametersPayload);
            try {
                FuzzingResult fuzzingResult = FuzzingStrategy.replaceField(result, target.path(),
                        FuzzingStrategy.replace().withData(value.value()), mergeFuzzing);
                result = fuzzingResult.json();
            } catch (RuntimeException _) {
                continue;
            }

            if (!before.equals(result)) {
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
            }
        }
        return result;
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
                        isIdentifierName(entry.getKey())) {
                    targets.add(new TargetField(path, entry.getKey(), location,
                            valueShape(entry.getValue().getAsJsonPrimitive())));
                }
            } else {
                collectReusableTargets(entry.getValue(), path, data, pathParametersPayload, targets);
            }
        }
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
        String resource = resourceName(data.getRelativePath());
        Set<String> targetAliases = semanticAliases(target.path(), target.name());
        boolean topLevelIdentifier = !target.path().contains("#") &&
                SIMPLE_IDENTIFIERS.contains(normalize(target.name()));
        return topLevelIdentifier || targetAliases.contains(resource + "id") ||
                targetAliases.contains(singular(resource) + "id");
    }

    private String bindingKey(String targetPath, TargetField target) {
        List<String> segments = FIELD_PATH_SPLITTER.splitToList(target.path());
        if (segments.size() > 1) {
            return normalize(segments.get(segments.size() - 2));
        }
        String key = normalize(target.name());
        for (String suffix : List.of("uuid", "guid", "id")) {
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
        return resolve(targetPath, target, boundResource, false);
    }

    private Optional<ResolvedValue> resolve(String targetPath, TargetField target, ResourceInstance boundResource,
                                            boolean boundOnly) {
        Set<String> targetAliases = semanticAliases(target.path(), target.name());
        List<ResolvedValue> candidates = resources.stream()
                .filter(ResourceInstance::active)
                .filter(resource -> !boundOnly || boundResource != null &&
                        resource.sequence() == boundResource.sequence())
                .flatMap(resource -> resource.values().values().stream()
                        .filter(value -> value.aliases().stream().anyMatch(targetAliases::contains))
                        .map(value -> new ResolvedValue(resource, value,
                                pathAffinity(resource.sourcePath(), targetPath) * 1_000 +
                                        Math.min(50, matchSpecificity(value, targetAliases)) * 10 +
                                        sourcePriority(resource) + storedValuePriority(value) +
                                        correlationFeedbackScore(resource, targetPath, target))))
                .toList();
        int bestQuality = candidates.stream()
                .mapToInt(candidate -> candidateQuality(candidate, targetPath, target, targetAliases, boundResource))
                .max().orElse(Integer.MIN_VALUE);
        return candidates.stream()
                .filter(candidate -> candidateQuality(candidate, targetPath, target, targetAliases, boundResource) == bestQuality)
                .max(Comparator.comparingInt(ResolvedValue::score)
                        .thenComparingLong(value -> value.resource().sequence()));
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

    private Set<String> primaryResourceIdentifierAliases(String path) {
        String resource = resourceName(path);
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(resource + "id");
        aliases.add(singular(resource) + "id");
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
        Map<String, StoredValue> enrichedValues = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            Set<String> aliases = new LinkedHashSet<>(value.aliases());
            aliases.add(normalize(key));
            if (value.aliases().stream().anyMatch(SIMPLE_IDENTIFIERS::contains)) {
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

    private void addFromLocation(ServiceData data, CatsResponse response, Map<String, StoredValue> pathValues,
                                 List<JsonObject> requestObjects) {
        Map<String, StoredValue> values = new LinkedHashMap<>(pathValues);
        if (requestObjects.size() == 1) {
            collectPrimitiveValues(requestObjects.getFirst(), "$.", "request.body.", values);
        }
        collectResponseHeaders(response, values);
        locationIdentifier(response).ifPresent(value -> values.put("id", value));
        addResource(data, values);
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
        extractIdentifierQueryValues(data, actualUrl).forEach(deletedValues::putIfAbsent);
        if (deletedValues.isEmpty()) {
            return;
        }
        for (int i = 0; i < resources.size(); i++) {
            ResourceInstance resource = resources.get(i);
            boolean matches = deletedValues.entrySet().stream().allMatch(deleted -> resource.values().values().stream()
                    .anyMatch(stored -> stored.aliases().contains(normalize(deleted.getKey())) &&
                            String.valueOf(stored.value()).equals(String.valueOf(deleted.getValue().value()))));
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
            return containsPrimitiveValue(JsonUtils.parseAsJsonElement(payload), values);
        } catch (RuntimeException _) {
            return false;
        }
    }

    private boolean containsPrimitiveValue(JsonElement element, Set<String> values) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonPrimitive()) {
            return values.contains(String.valueOf(primitiveValue(element.getAsJsonPrimitive())));
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().asList().stream()
                    .anyMatch(item -> containsPrimitiveValue(item, values));
        }
        return element.getAsJsonObject().entrySet().stream()
                .anyMatch(entry -> containsPrimitiveValue(entry.getValue(), values));
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
        List<JsonObject> objects = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (element.isJsonObject() && objects.size() < MAX_COLLECTION_ITEMS) {
                objects.add(element.getAsJsonObject());
            }
        }
        return objects;
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
        aliases.add(normalize(name));
        List<String> segments = SEMANTIC_PATH_SPLITTER.splitToList(path.replace("$", ""));
        StringBuilder suffix = new StringBuilder();
        for (int i = segments.size() - 1; i >= 0; i--) {
            suffix.insert(0, segments.get(i));
            aliases.add(normalize(suffix.toString()));
        }
        return Set.copyOf(aliases);
    }

    private boolean isIdentifierName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return SIMPLE_IDENTIFIERS.contains(lower) || SEPARATED_ID.matcher(name).matches() ||
                name.endsWith("Id") || name.endsWith("ID") || name.endsWith("Uuid") || name.endsWith("UUID") ||
                name.endsWith("Guid") || name.endsWith("GUID");
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
        Set<String> aliases = new LinkedHashSet<>();
        pathSegments(path).stream()
                .filter(segment -> !isTemplateSegment(segment))
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
