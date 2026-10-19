package com.endava.cats.pool;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.OpenApiUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Passively records resource identifiers returned by successful requests and reuses them in later requests.
 * No additional service calls are made by this component.
 */
@ApplicationScoped
public class RuntimeResourcePool {
    private static final int MAX_RESOURCES = 500;
    private static final int MAX_CORRELATION_FEEDBACK = 5_000;
    private static final int MAX_FEEDBACK_REWARD = 500;
    private static final int MIN_FEEDBACK_PENALTY = -5_000;
    private static final int SUCCESS_REWARD = 100;
    private static final int NOT_FOUND_PENALTY = -1_000;
    private static final int CONFLICT_PENALTY = -200;

    private final ProcessingArguments processingArguments;
    private final List<ResourceInstance> resources = new ArrayList<>();
    private final SuccessfulBaselineStore baselineStore = new SuccessfulBaselineStore();
    private final SuccessfulExchangeRecorder exchangeRecorder = new SuccessfulExchangeRecorder();
    private final ResourceRequestEnricher enricher;
    private final Map<CorrelationFeedbackKey, Integer> correlationFeedback = new LinkedHashMap<>();
    private final IdentityHashMap<ResolvedRequest, Map<ResourceCorrelation, Long>> pendingCorrelationSources = new IdentityHashMap<>();
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
        this.enricher = new ResourceRequestEnricher(filesArguments, resources,
                (resource, lookup) -> correlationFeedbackScore(resource, lookup.targetPath(), lookup.target()));
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
        String enrichedPathParams = enricher.enrichJson(data.getPathParamsPayload(), data, correlations, correlationSources,
                requestBindings, true);
        String enrichedPayload = enricher.enrichJson(processedPayload, data, correlations, correlationSources,
                requestBindings, false);
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
        enricher.enrichJson(data.getPathParamsPayload(), data, correlations, correlationSources, requestBindings, true);
        return enricher.enrichJson(processedPayload, data, correlations, correlationSources, requestBindings, false);
    }

    public synchronized Map<String, String> resolvePathParameters(ServiceData data) {
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
                    enricher.pathParameterShape(data, name));
            if (data.isFuzzedField(name, RequestTarget.Location.PATH) || enricher.hasExplicitValue(data, target)) {
                continue;
            }
            String bindingKey = ResourceSemanticNaming.bindingKey(data.getRelativePath(), target);
            ResourceInstance bound = requestBindings.getOrDefault(bindingKey, pathBoundResource[0]);
            enricher.resolve(data.getRelativePath(), target, bound, false, data.getHttpMethod()).ifPresent(value -> {
                resolvedParameters.put(name, String.valueOf(value.value()));
                requestBindings.put(bindingKey, value.resource());
                if (pathBoundResource[0] == null) {
                    pathBoundResource[0] = value.resource();
                }
            });
        }
        return Map.copyOf(resolvedParameters);
    }

    public synchronized ResolvedHeaders enrichHeaders(ServiceData data, List<KeyValuePair<String, Object>> headers) {
        return enrichHeaders(data, headers, null);
    }

    public synchronized ResolvedHeaders enrichHeaders(ServiceData data, List<KeyValuePair<String, Object>> headers,
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
        if (enricher.hasExplicitHeader(data, name)) {
            return;
        }
        Object currentValue = headers.stream().filter(header -> header.getKey().equalsIgnoreCase(name))
                .map(KeyValuePair::getValue).findFirst().orElse("");
        TargetField target = new TargetField(name, name, RequestTarget.Location.HEADER,
                ResourceSemanticNaming.valueShape(currentValue));
        String bindingKey = ResourceSemanticNaming.bindingKey(data.getRelativePath(), target);
        ResourceInstance boundResource = pathResource == null ? requestBindings.get(bindingKey) : pathResource;
        enricher.resolve(data.getRelativePath(), target, boundResource, pathResource != null).ifPresent(value -> {
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

    public synchronized String applySuccessfulRequestBaseline(ServiceData data) {
        return baselineStore.apply(data, processingArguments.isReuseSuccessfulResources());
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

    public synchronized void observe(ServiceData data, CatsRequest request, CatsResponse response,
                                     ResolvedRequest resolvedRequest) {
        if (!processingArguments.isReuseSuccessfulResources()) {
            return;
        }
        String effectiveRequestPayload = effectiveJsonPayload(request, resolvedRequest);
        exchangeRecorder.recordOperationDiagnostics(data, request, response, resolvedRequest, effectiveRequestPayload,
                d -> exchangeRecorder.buildDiagnosticTargets(d,
                        (sd, tf) -> enricher.hasExplicitValue(sd, tf) || enricher.shouldSkipOwnPostIdentifier(sd, tf),
                        pathParams -> enricher.reusableTargets(pathParams ? d.getPathParamsPayload() : d.getPayload(), d, pathParams)));
        updateCorrelationFeedback(data, response, resolvedRequest);
        if (!ResponseCodeFamily.is2xxCode(response.getResponseCode())) {
            return;
        }
        exchangeRecorder.recordSuccessfulExchange(data, request, response);
        baselineStore.store(data, effectiveRequestPayload);
        if (data.getHttpMethod() == HttpMethod.DELETE) {
            markDeleted(data, request.getUrl());
            return;
        }
        if (!isResourceProducingMethod(data.getHttpMethod())) {
            return;
        }
        Map<String, StoredValue> pathValues = ResourceValueExtractor.extractPathValues(data.getRelativePath(), request.getUrl());
        List<JsonObject> requestObjects = HttpMethod.requiresBody(data.getHttpMethod()) &&
                !ResourceValueExtractor.isRequestBodyFuzzed(data)
                ? ResourceValueExtractor.requestObjects(effectiveRequestPayload) : List.of();
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
                (!ResourceSemanticNaming.isCollectionPath(data.getRelativePath()) ||
                        !ResourceValueExtractor.isCollectionResponse(response.getBody()));
    }

    private void addResourceFromReusableIndicators(ServiceData data, CatsResponse response, Map<String, StoredValue> pathValues) {
        boolean reusableHeaders = ResourceValueExtractor.collectResponseHeaders(response, pathValues);
        boolean reusableItemPath = !ResourceSemanticNaming.isCollectionPath(data.getRelativePath()) && !pathValues.isEmpty() &&
                OpenApiUtils.getPathVariables(data.getRelativePath()).stream()
                        .noneMatch(name -> data.isFuzzedField(name, RequestTarget.Location.PATH) ||
                                enricher.wasModifiedByFuzzer(data, name, false));
        if (ResourceSemanticNaming.isCollectionPath(data.getRelativePath()) && JsonUtils.isValidJson(response.getBody())) {
            try {
                JsonElement root = JsonUtils.parseAsJsonElement(response.getBody());
                if (root != null && root.isJsonObject()) {
                    ResourceValueExtractor.collectPrimitiveValues(root.getAsJsonObject(), "$.", "response.body.", pathValues);
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
        List<ResponseObject> responseObjects = ResourceValueExtractor.responseObjects(
                response.getBody(), data.getHttpMethod() == HttpMethod.GET);
        if (responseObjects.isEmpty()) {
            addFromLocation(data, response, pathValues, requestObjects);
            return;
        }
        boolean singleResponseObject = responseObjects.size() == 1;
        Optional<StoredValue> locationIdentifier = singleResponseObject
                ? ResourceValueExtractor.locationIdentifier(response) : Optional.empty();
        boolean alignedRequestObjects = ResourceValueExtractor.alignedObjectPayloads(
                effectiveRequestPayload, response.getBody(), requestObjects.size(), responseObjects.size());
        int limit = Math.min(responseObjects.size(), ResourceValueExtractor.MAX_COLLECTION_ITEMS);
        for (int i = 0; i < limit; i++) {
            Map<String, StoredValue> values = new LinkedHashMap<>(pathValues);
            ResponseObject responseObject = responseObjects.get(i);
            ResourceValueExtractor.collectPrimitiveValues(
                    responseObject.value(), responseObject.sourcePrefix(), "response.body.", values);
            if (alignedRequestObjects) {
                ResourceValueExtractor.collectPrimitiveValues(requestObjects.get(i), "$.", "request.body.", values);
            }
            if (singleResponseObject) {
                ResourceValueExtractor.collectResponseHeaders(response, values);
            }
            ResourceValueExtractor.collectKeyedMapIdentifier(data.getRelativePath(), responseObject.value(), values);
            assignFallbackIdentifier(data.getRelativePath(), locationIdentifier, values);
            addResource(data, values);
        }
        if (!singleResponseObject) {
            Map<String, StoredValue> headerValues = new LinkedHashMap<>(pathValues);
            if (ResourceValueExtractor.collectResponseHeaders(response, headerValues)) {
                addResource(data, headerValues);
            }
        }
    }

    private void assignFallbackIdentifier(String relativePath, Optional<StoredValue> locationIdentifier,
                                          Map<String, StoredValue> values) {
        if (ResourceValueExtractor.hasResourceIdentifier(values, relativePath)) {
            return;
        }
        locationIdentifier.or(() -> ResourceValueExtractor.wrapperIdentifier(values))
                .ifPresent(value -> values.put("id", value));
    }

    private void addFromLocation(ServiceData data, CatsResponse response, Map<String, StoredValue> pathValues,
                                 List<JsonObject> requestObjects) {
        Map<String, StoredValue> values = new LinkedHashMap<>(pathValues);
        if (requestObjects.size() == 1) {
            ResourceValueExtractor.collectPrimitiveValues(requestObjects.getFirst(), "$.", "request.body.", values);
        }
        ResourceValueExtractor.collectResponseHeaders(response, values);
        Optional<StoredValue> locId = ResourceValueExtractor.locationIdentifier(response);
        if (locId.isPresent()) {
            values.put("id", locId.get());
        } else if (data.getHttpMethod() == HttpMethod.POST && response.getBody() != null) {
            String trimmedBody = response.getBody().trim().replace("\"", "");
            if (ResourceValueExtractor.isScalarIdentifierBody(trimmedBody)) {
                values.put("id", new StoredValue(trimmedBody, "response.body.$", Set.of("id")));
            }
        } else if (data.getHttpMethod() == HttpMethod.GET &&
                ResourceSemanticNaming.isCollectionPath(data.getRelativePath()) &&
                JsonUtils.isValidJson(response.getBody())) {
            JsonElement root = JsonUtils.parseAsJsonElement(response.getBody());
            if (ResourceValueExtractor.isPrimitiveArray(root)) {
                Object val = ResourceSemanticNaming.primitiveValue(root.getAsJsonArray().get(0).getAsJsonPrimitive());
                String resource = ResourceSemanticNaming.resourceName(data.getRelativePath());
                String singular = ResourceSemanticNaming.singular(resource);
                Set<String> aliases = new LinkedHashSet<>(List.of(resource, singular, resource + "id", singular + "id"));
                values.put("$[]", new StoredValue(val, "response.body.$[]", Set.copyOf(aliases)));
            }
        }
        addResource(data, values);
    }

    private void addResource(ServiceData data, Map<String, StoredValue> values) {
        if (values.isEmpty()) {
            return;
        }
        Set<String> resourceIdentifierAliases = ResourceSemanticNaming.resourceIdentifierAliases(data.getRelativePath());
        boolean hasRootSimpleIdentifier = values.entrySet().stream()
                .anyMatch(entry -> ResourceSemanticNaming.isTopLevelResponseKey(entry.getKey()) &&
                        entry.getValue().aliases().stream().anyMatch(ResourceSemanticNaming.SIMPLE_IDENTIFIERS::contains));
        Map<String, StoredValue> enrichedValues = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            Set<String> aliases = new LinkedHashSet<>(value.aliases());
            aliases.add(ResourceSemanticNaming.normalize(key));
            if (hasRootSimpleIdentifier && !ResourceSemanticNaming.isTopLevelResponseKey(key)) {
                aliases.removeAll(ResourceSemanticNaming.SIMPLE_IDENTIFIERS);
            } else if (value.aliases().stream().anyMatch(ResourceSemanticNaming.SIMPLE_IDENTIFIERS::contains)) {
                aliases.addAll(resourceIdentifierAliases);
            }
            enrichedValues.put(key, value.withAliases(aliases));
        });

        resources.add(new ResourceInstance(sequence.incrementAndGet(), data.getRelativePath(), data.getHttpMethod(),
                Collections.unmodifiableMap(enrichedValues), ResourceValueExtractor.isFuzzedRequest(data), true));
        if (resources.size() > MAX_RESOURCES) {
            ResourceInstance removed = resources.removeFirst();
            correlationFeedback.keySet().removeIf(key -> key.resourceSequence() == removed.sequence());
        }
    }

    private void markDeleted(ServiceData data, String actualUrl) {
        Map<String, StoredValue> deletedValues = new LinkedHashMap<>(
                ResourceValueExtractor.extractPathValues(data.getRelativePath(), actualUrl));
        if (deletedValues.isEmpty()) {
            ResourceValueExtractor.extractIdentifierQueryValues(data, actualUrl).forEach(deletedValues::putIfAbsent);
        }
        if (deletedValues.isEmpty()) {
            return;
        }
        for (int i = 0; i < resources.size(); i++) {
            ResourceInstance resource = resources.get(i);
            boolean matches = deletedValues.entrySet().stream().allMatch(deleted -> {
                Set<String> deletedAliases = ResourceSemanticNaming.deletedAliases(
                        data.getRelativePath(), deleted.getKey(), deleted.getValue());
                return resource.values().values().stream()
                        .anyMatch(stored -> stored.aliases().stream().anyMatch(deletedAliases::contains) &&
                                ResourceSemanticNaming.sameValue(stored.value(), deleted.getValue().value()));
            });
            if (matches) {
                resources.set(i, resource.deleted());
                correlationFeedback.keySet().removeIf(key -> key.resourceSequence() == resource.sequence());
            }
        }
        Set<String> deletedPrimitiveValues = deletedValues.values().stream()
                .map(StoredValue::value).map(String::valueOf).collect(Collectors.toSet());
        baselineStore.evictContainingPrimitiveValues(deletedPrimitiveValues);
    }

    /**
     * Clears all values captured by the current run.
     */
    public synchronized void clear() {
        resources.clear();
        baselineStore.clear();
        correlationFeedback.clear();
        pendingCorrelationSources.clear();
        exchangeRecorder.clear();
        sequence.set(0);
    }

    public synchronized List<UnresolvedOperation> unresolvedOperations() {
        return exchangeRecorder.unresolvedOperations();
    }

    public synchronized boolean hasSucceededWithUnmodifiedRequest(String path, HttpMethod method) {
        return exchangeRecorder.hasSucceededWithUnmodifiedRequest(path, method);
    }

    public synchronized List<SuccessfulExchange> successfulExchanges(String path, HttpMethod method) {
        return exchangeRecorder.successfulExchanges(path, method);
    }

    public synchronized Optional<String> alternativePathParameterValue(String targetPath, String parameterName,
                                                                       String currentValue) {
        return enricher.alternativePathParameterValue(targetPath, parameterName, currentValue);
    }

    public synchronized void discard(ResolvedRequest resolvedRequest) {
        pendingCorrelationSources.remove(resolvedRequest);
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

    private void updateCorrelationFeedback(ServiceData data, CatsResponse response, ResolvedRequest resolvedRequest) {
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
        if (ResourceValueExtractor.isFuzzedRequest(data)) {
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

    private int correlationFeedbackScore(ResourceInstance resource, String targetPath, TargetField target) {
        CorrelationFeedbackKey key = new CorrelationFeedbackKey(resource.sequence(), targetPath,
                new RequestTarget(target.location(), target.path()));
        return correlationFeedback.getOrDefault(key, 0);
    }

    /**
     * Result of applying runtime resource correlations to request data.
     */
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

    /**
     * Small in-memory evidence snapshot for second-phase checks. Request headers are never copied into this record.
     */
    public record SuccessfulExchange(String path, HttpMethod method, String url, String requestBody,
                                     int status, String responseBody, String location, String etag) {
        @Override
        public String toString() {
            return "SuccessfulExchange[method=%s, path=%s, status=%d]".formatted(method, path, status);
        }
    }

    /**
     * Why an identifier parameter is considered unresolved.
     */
    public enum UnresolvedReason {
        /**
         * No matching value was captured from earlier successful responses.
         */
        NO_VALUE_CAPTURED,
        /**
         * Values reused from earlier responses always returned 404 or 410.
         */
        CAPTURED_VALUES_NOT_FOUND
    }

    /**
     * Aggregated outcome of the unmodified requests sent to an operation.
     */
    public record OperationOutcome(String path, HttpMethod method, int requests, int successes,
                                   Map<Integer, Integer> statusCodes) {
        public OperationOutcome {
            statusCodes = Collections.unmodifiableMap(new LinkedHashMap<>(statusCodes));
        }
    }

    /**
     * An identifier parameter that could not be resolved at runtime.
     */
    public record UnresolvedParameter(RequestTarget target, UnresolvedReason reason, OperationOutcome producer) {
    }

    /**
     * An operation that never succeeded and depends on unresolved identifier parameters.
     */
    public record UnresolvedOperation(OperationOutcome outcome, List<UnresolvedParameter> parameters) {
    }

    private record CorrelationFeedbackKey(long resourceSequence, String targetPath, RequestTarget target) {
    }
}
