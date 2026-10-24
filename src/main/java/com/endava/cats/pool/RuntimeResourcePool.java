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
import com.endava.cats.util.WordUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

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
    private static final int MAX_RESOURCES_PER_PATH = 50;
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
    private final Map<PairFeedbackKey, Integer> pairFeedback = new LinkedHashMap<>();
    private final Set<String> observedProducers = new LinkedHashSet<>();
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
                (resource, lookup) -> correlationFeedbackScore(
                        resource, lookup.sourceLocation(), lookup.targetPath(), lookup.target()));
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
        if (!processingArguments.isReuseSuccessfulResources() ||
                (!data.isReplaceRefData() && !data.isReplaceUrlParams()) || resources.isEmpty()) {
            return new ResolvedRequest(processedPayload, data.getPathParamsPayload(), List.of());
        }

        List<ResourceCorrelation> correlations = new ArrayList<>();
        Map<ResourceCorrelation, Long> correlationSources = new LinkedHashMap<>();
        Map<String, ResourceInstance> requestBindings = new LinkedHashMap<>();
        String enrichedPathParams = data.isReplaceUrlParams()
                ? enricher.enrichJson(data.getPathParamsPayload(), data, correlations, correlationSources,
                        requestBindings, true)
                : data.getPathParamsPayload();
        String enrichedPayload = data.isReplaceRefData()
                ? enricher.enrichJson(processedPayload, data, correlations, correlationSources,
                        requestBindings, false)
                : processedPayload;
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
            enricher.resolve(data.getRelativePath(), target, bound, false, data.getHttpMethod(), data).ifPresent(value -> {
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
        if (!ResourceSemanticNaming.isCorrelatableHeader(name) || enricher.hasExplicitHeader(data, name)) {
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
                        pathParams -> {
                            boolean isPathParams = Boolean.TRUE.equals(pathParams);
                            return enricher.reusableTargets(isPathParams ? d.getPathParamsPayload() : d.getPayload(), d, isPathParams);
                        }));
        updateCorrelationFeedback(data, response, resolvedRequest);
        if (!ResponseCodeFamily.is2xxCode(response.getResponseCode())) {
            return;
        }
        exchangeRecorder.recordSuccessfulExchange(data, request, response);
        if (data.getHttpMethod() == HttpMethod.DELETE) {
            markDeleted(data, request.getUrl(), resolvedRequest);
            return;
        }
        baselineStore.store(data, effectiveRequestPayload);
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
        boolean fuzzedRequest = ResourceValueExtractor.isFuzzedRequest(data);
        int limit = Math.min(responseObjects.size(), ResourceValueExtractor.MAX_COLLECTION_ITEMS);
        String collectionResource = ResourceSemanticNaming.lastRawResourceSegment(data.getRelativePath());
        Optional<String> inferredCollectionKey = data.getHttpMethod() == HttpMethod.GET &&
                ResourceSemanticNaming.isCollectionPath(data.getRelativePath())
                ? ResourceValueExtractor.inferObjectArrayKeyField(
                        responseObjects.subList(0, limit).stream().map(ResponseObject::value).toList(),
                        collectionResource)
                : Optional.empty();
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
            if (!ResourceValueExtractor.hasResourceIdentifier(values, data.getRelativePath()) && inferredCollectionKey.isPresent()) {
                ResourceValueExtractor.applyInferredKeyAlias(
                        collectionResource, responseObject.sourcePrefix() + inferredCollectionKey.get(), values);
            }
            assignFallbackIdentifier(data.getRelativePath(), locationIdentifier, values);
            addResource(data, values, fuzzedRequest);
        }
        if (!singleResponseObject) {
            Map<String, StoredValue> headerValues = new LinkedHashMap<>(pathValues);
            if (ResourceValueExtractor.collectResponseHeaders(response, headerValues)) {
                addResource(data, headerValues, fuzzedRequest);
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
                Set<String> aliases = new LinkedHashSet<>(List.of(resource, resource + "id"));
                for (String form : WordUtils.nounForms(resource)) {
                    aliases.add(form);
                    aliases.add(form + "id");
                }
                values.put("$[]", new StoredValue(val, "response.body.$[]", Set.copyOf(aliases)));
            }
        } else if (data.getHttpMethod() == HttpMethod.POST
                || (data.getHttpMethod() == HttpMethod.GET && ResourceSemanticNaming.isCollectionPath(data.getRelativePath()))) {
            ResourceValueExtractor.extractXmlIdentifier(response.getBody()).ifPresent(parsedXmlId -> {
                Set<String> aliases = new LinkedHashSet<>(Set.of("id"));
                aliases.addAll(ResourceSemanticNaming.primaryResourceIdentifierAliases(data.getRelativePath()));
                StoredValue storedXmlId = new StoredValue(parsedXmlId, "response.body.xml.id", Set.copyOf(aliases));
                values.put("id", storedXmlId);
                values.put("$.id", storedXmlId);
            });
        }
        addResource(data, values, ResourceValueExtractor.isFuzzedRequest(data));
    }

    private void addResource(ServiceData data, Map<String, StoredValue> values) {
        addResource(data, values, ResourceValueExtractor.isFuzzedRequest(data));
    }

    private void addResource(ServiceData data, Map<String, StoredValue> values, boolean fuzzedRequest) {
        if (values.isEmpty()) {
            return;
        }
        Set<String> resourceIdentifierAliases = ResourceSemanticNaming.resourceIdentifierAliases(data.getRelativePath());
        Set<String> resourceNounForms = ResourceSemanticNaming.resourceWithTailWordForms(
                ResourceSemanticNaming.lastRawResourceSegment(data.getRelativePath()));
        boolean hasRootSimpleIdentifier = values.entrySet().stream()
                .anyMatch(entry -> ResourceSemanticNaming.isTopLevelResponseKey(entry.getKey()) &&
                        entry.getValue().aliases().stream().anyMatch(ResourceSemanticNaming.SIMPLE_IDENTIFIERS::contains));
        Map<String, StoredValue> enrichedValues = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            Set<String> aliases = new LinkedHashSet<>(value.aliases());
            String normKey = ResourceSemanticNaming.normalize(key);
            aliases.add(normKey);
            if (hasRootSimpleIdentifier && !ResourceSemanticNaming.isTopLevelResponseKey(key)) {
                aliases.removeAll(ResourceSemanticNaming.SIMPLE_IDENTIFIERS);
            } else if (value.aliases().stream().anyMatch(ResourceSemanticNaming.SIMPLE_IDENTIFIERS::contains)
                    || (!hasRootSimpleIdentifier && ResourceSemanticNaming.isTopLevelResponseKey(key) && resourceNounForms.contains(normKey))) {
                aliases.add("id");
                aliases.addAll(resourceIdentifierAliases);
            }
            enrichedValues.put(key, value.withAliases(aliases));
        });

        if (data.getHttpMethod() == HttpMethod.POST) {
            observedProducers.add(data.getRelativePath());
        }
        String primaryId = extractPrimaryIdentifierValue(enrichedValues, resourceIdentifierAliases);
        if (deduplicateExistingResource(data, enrichedValues, fuzzedRequest, resourceIdentifierAliases, primaryId)) {
            return;
        }
        if (!enforcePerPathCap(data.getRelativePath(), data.getHttpMethod(), fuzzedRequest)) {
            return;
        }
        resources.add(new ResourceInstance(sequence.incrementAndGet(), data.getRelativePath(), data.getHttpMethod(),
                Collections.unmodifiableMap(enrichedValues), fuzzedRequest, true));
        if (resources.size() > MAX_RESOURCES) {
            evictLeastValuableResource();
        }
    }

    private String extractPrimaryIdentifierValue(Map<String, StoredValue> values, Set<String> resourceIdAliases) {
        for (Map.Entry<String, StoredValue> entry : values.entrySet()) {
            if (ResourceSemanticNaming.isTopLevelResponseKey(entry.getKey()) &&
                    entry.getValue().aliases().stream().anyMatch(a ->
                            ResourceSemanticNaming.SIMPLE_IDENTIFIERS.contains(a) || resourceIdAliases.contains(a))) {
                return String.valueOf(entry.getValue().value());
            }
        }
        return null;
    }

    /**
     * Checks whether the given value matches any identifier-like stored value in an active resource in this pool.
     *
     * @param candidate candidate value to check
     * @return true if the value is already present as a resource identifier in the pool
     */
    public synchronized boolean isKnownPoolValue(Object candidate) {
        if (candidate == null) {
            return false;
        }
        for (ResourceInstance resource : resources) {
            if (!resource.active()) {
                continue;
            }
            for (StoredValue stored : resource.values().values()) {
                if (isIdentifierStoredValue(stored) && ResourceSemanticNaming.sameValue(candidate, stored.value())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isIdentifierStoredValue(StoredValue stored) {
        for (String alias : stored.aliases()) {
            if (ResourceSemanticNaming.SIMPLE_IDENTIFIERS.contains(alias) ||
                    alias.endsWith("id") || alias.endsWith("uuid") || alias.endsWith("guid")) {
                return true;
            }
        }
        return false;
    }

    private boolean deduplicateExistingResource(ServiceData data, Map<String, StoredValue> enrichedValues,
                                                boolean fuzzedRequest, Set<String> resourceIdAliases, String primaryId) {
        if (primaryId == null) {
            return false;
        }
        Set<String> incomingForms = WordUtils.nounForms(ResourceSemanticNaming.resourceName(data.getRelativePath()));
        int index = findMatchingResourceIndex(incomingForms, resourceIdAliases, primaryId);
        if (index < 0) {
            return false;
        }
        ResourceInstance existing = resources.get(index);
        if (shouldIgnoreFuzzedGetUpdate(data, fuzzedRequest, existing)) {
            return true;
        }
        existing.values().forEach(enrichedValues::putIfAbsent);
        HttpMethod mergedMethod = existing.sourceMethod() == HttpMethod.POST ? HttpMethod.POST : data.getHttpMethod();
        boolean mergedFuzzed = existing.fuzzed() && fuzzedRequest;
        String mergedPath = existing.sourceMethod() == HttpMethod.POST ? existing.sourcePath() : data.getRelativePath();
        resources.remove(index);
        resources.add(new ResourceInstance(existing.sequence(), mergedPath, mergedMethod,
                Collections.unmodifiableMap(enrichedValues), mergedFuzzed, true));
        return true;
    }

    private int findMatchingResourceIndex(Set<String> incomingForms, Set<String> resourceIdAliases, String primaryId) {
        for (int i = 0; i < resources.size(); i++) {
            ResourceInstance existing = resources.get(i);
            if (existing.active() && !Collections.disjoint(existing.resourceNameForms(), incomingForms) &&
                    primaryId.equals(extractPrimaryIdentifierValue(existing.values(), resourceIdAliases))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean shouldIgnoreFuzzedGetUpdate(ServiceData data, boolean fuzzedRequest, ResourceInstance existing) {
        return fuzzedRequest && data.getHttpMethod() == HttpMethod.GET &&
                (!existing.fuzzed() || existing.sourceMethod() != HttpMethod.GET);
    }

    private boolean enforcePerPathCap(String path, HttpMethod method, boolean fuzzedRequest) {
        int pathCount = 0;
        int evictIndex = -1;
        int bestEvictRank = -1;
        for (int i = 0; i < resources.size(); i++) {
            ResourceInstance r = resources.get(i);
            if (r.sourcePath().equals(path)) {
                pathCount++;
                int rank = evictionRank(r);
                if (rank > bestEvictRank) {
                    bestEvictRank = rank;
                    evictIndex = i;
                }
            }
        }
        if (pathCount < MAX_RESOURCES_PER_PATH) {
            return true;
        }
        if (incomingEvictionRank(method, fuzzedRequest) > bestEvictRank) {
            return false;
        }
        removeResourceAt(evictIndex);
        return true;
    }

    private static int incomingEvictionRank(HttpMethod method, boolean fuzzedRequest) {
        if (fuzzedRequest) {
            return method == HttpMethod.GET ? 4 : 3;
        }
        if (method == HttpMethod.GET) {
            return 2;
        }
        return method == HttpMethod.POST ? 0 : 1;
    }

    private void evictLeastValuableResource() {
        int evictIndex = 0;
        int bestRank = -1;
        for (int i = 0; i < resources.size(); i++) {
            int rank = evictionRank(resources.get(i));
            if (rank > bestRank) {
                bestRank = rank;
                evictIndex = i;
            }
        }
        removeResourceAt(evictIndex);
    }

    private static int evictionRank(ResourceInstance r) {
        if (!r.active()) {
            return 5;
        }
        if (r.fuzzed() && r.sourceMethod() == HttpMethod.GET) {
            return 4;
        }
        if (r.fuzzed()) {
            return 3;
        }
        if (r.sourceMethod() == HttpMethod.GET) {
            return 2;
        }
        return r.sourceMethod() == HttpMethod.POST ? 0 : 1;
    }

    private void removeResourceAt(int index) {
        ResourceInstance removed = resources.remove(index);
        correlationFeedback.keySet().removeIf(key -> key.resourceSequence() == removed.sequence());
    }

    private void markDeleted(ServiceData data, String actualUrl, ResolvedRequest resolvedRequest) {
        Map<String, StoredValue> deletedValues = new LinkedHashMap<>(
                ResourceValueExtractor.extractPathValues(data.getRelativePath(), actualUrl));
        if (resolvedRequest != null && resolvedRequest.correlations() != null) {
            for (ResourceCorrelation correlation : resolvedRequest.correlations()) {
                if (correlation.getTarget().location() == RequestTarget.Location.PATH && correlation.getValue() != null) {
                    String name = correlation.getTarget().name();
                    deletedValues.put(name, new StoredValue(correlation.getValue(), "request.path." + name,
                            Set.of(ResourceSemanticNaming.normalize(name))));
                }
            }
        }
        if (deletedValues.isEmpty()) {
            ResourceValueExtractor.extractIdentifierQueryValues(data, actualUrl).forEach(deletedValues::putIfAbsent);
        }
        if (deletedValues.isEmpty()) {
            return;
        }
        Set<String> deletedPrimitiveStrings = deletedValues.values().stream()
                .map(StoredValue::value)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toSet());
        baselineStore.evictContainingPrimitiveValues(deletedPrimitiveStrings);
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
    }

    /**
     * Clears all values captured by the current run.
     */
    public synchronized void clear() {
        resources.clear();
        baselineStore.clear();
        correlationFeedback.clear();
        pairFeedback.clear();
        observedProducers.clear();
        pendingCorrelationSources.clear();
        exchangeRecorder.clear();
        sequence.set(0);
    }

    /**
     * Returns the monotonic count of distinct {@code (method, path)} operations that have produced at least one
     * resource in this pool. Unlike {@code resources.size()}, this count is unaffected by {@code MAX_RESOURCES}
     * eviction and only advances when a previously non-producing operation yields a resource for the first time.
     */
    public synchronized long observedResourceCount() {
        return observedProducers.size();
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
        if (correlationSources == null || correlationSources.isEmpty()) {
            return;
        }
        int adjustment = feedbackAdjustment(data, response.getResponseCode(), correlationSources.size());
        if (adjustment == 0) {
            return;
        }
        Set<CorrelationFeedbackKey> keys = new LinkedHashSet<>();
        Set<PairFeedbackKey> pairKeys = new LinkedHashSet<>();
        correlationSources.forEach((correlation, resourceSequence) -> {
            keys.add(new CorrelationFeedbackKey(resourceSequence, data.getRelativePath(), correlation.getTarget()));
            if (correlation.getSourcePath() != null && correlation.getSourceLocation() != null) {
                pairKeys.add(new PairFeedbackKey(correlation.getSourcePath(), correlation.getSourceLocation(),
                        data.getRelativePath(), correlation.getTarget()));
            }
        });
        keys.forEach(key -> updateCorrelationFeedback(key, adjustment));
        pairKeys.forEach(pairKey -> updatePairFeedback(pairKey, adjustment));
    }

    private int feedbackAdjustment(ServiceData data, int responseCode, int correlatedTargetsCount) {
        if (ResponseCodeFamily.is2xxCode(responseCode)) {
            return SUCCESS_REWARD;
        }
        if (ResourceValueExtractor.isFuzzedRequest(data)) {
            return 0;
        }
        return switch (responseCode) {
            case 404, 410 -> NOT_FOUND_PENALTY;
            case 409, 422 -> CONFLICT_PENALTY;
            case 400 -> correlatedTargetsCount == 1 && isSingleInputRequest(data) ? CONFLICT_PENALTY : 0;
            default -> 0;
        };
    }

    private boolean isSingleInputRequest(ServiceData data) {
        if (HttpMethod.requiresBody(data.getHttpMethod()) && StringUtils.isNotBlank(data.getPathParamsPayload()) &&
                !JsonUtils.isEmptyPayload(data.getPayload())) {
            return false;
        }
        if (!JsonUtils.isValidJson(data.getPayload())) {
            return false;
        }
        JsonElement el = JsonUtils.parseAsJsonElement(data.getPayload());
        return el.isJsonObject() && el.getAsJsonObject().size() == 1;
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

    private void updatePairFeedback(PairFeedbackKey key, int adjustment) {
        pairFeedback.compute(key, (_, current) -> {
            int previous = Optional.ofNullable(current).orElse(0);
            if (adjustment > 0) {
                return Math.min(MAX_FEEDBACK_REWARD, Math.max(0, previous) + adjustment);
            }
            return Math.max(MIN_FEEDBACK_PENALTY, Math.min(0, previous) + adjustment);
        });
        while (pairFeedback.size() > MAX_CORRELATION_FEEDBACK) {
            pairFeedback.remove(pairFeedback.keySet().iterator().next());
        }
    }

    private int correlationFeedbackScore(ResourceInstance resource, String sourceLocation,
                                         String targetPath, TargetField target) {
        RequestTarget reqTarget = new RequestTarget(target.location(), target.path());
        CorrelationFeedbackKey key = new CorrelationFeedbackKey(resource.sequence(), targetPath, reqTarget);
        int instanceScore = correlationFeedback.getOrDefault(key, 0);
        int pairScore = sourceLocation == null ? 0 : pairFeedback.getOrDefault(
                new PairFeedbackKey(resource.sourcePath(), sourceLocation, targetPath, reqTarget), 0);
        return Math.clamp((long) instanceScore + pairScore, MIN_FEEDBACK_PENALTY, MAX_FEEDBACK_REWARD);
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

    private record PairFeedbackKey(String sourcePath, String sourceLocation, String targetPath, RequestTarget target) {
    }
}
