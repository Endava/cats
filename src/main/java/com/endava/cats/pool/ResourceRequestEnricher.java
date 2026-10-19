package com.endava.cats.pool;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;

/**
 * Resolves and enriches request payloads, path parameters, and headers using active resources in {@link RuntimeResourcePool}.
 */
final class ResourceRequestEnricher {
    private static final int MIN_FEEDBACK_PENALTY = -5_000;

    private final FilesArguments filesArguments;
    private final List<ResourceInstance> resources;
    private final ToIntBiFunction<ResourceInstance, FeedbackLookup> feedbackLookup;

    record FeedbackLookup(String targetPath, TargetField target) {
    }

    ResourceRequestEnricher(FilesArguments filesArguments,
                            List<ResourceInstance> resources,
                            ToIntBiFunction<ResourceInstance, FeedbackLookup> feedbackLookup) {
        this.filesArguments = filesArguments;
        this.resources = resources;
        this.feedbackLookup = feedbackLookup;
    }

    String enrichJson(String json, ServiceData data, List<ResourceCorrelation> correlations,
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
        String bindingKey = ResourceSemanticNaming.bindingKey(data.getRelativePath(), target);
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
        List<String> segments = ResourceSemanticNaming.pathSegments(data.getRelativePath());
        if (segments.size() < 2 || ResourceSemanticNaming.isTemplateSegment(segments.getLast()) ||
                ResourceSemanticNaming.isTemplateSegment(segments.get(segments.size() - 2))) {
            return Optional.empty();
        }
        String parentResource = ResourceSemanticNaming.normalize(segments.get(segments.size() - 2));
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

    List<TargetField> reusableTargets(String json, ServiceData data, boolean pathParametersPayload) {
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
                        ResourceSemanticNaming.isIdentifierName(entry.getKey()) || hasSiblingPostValue(data, entry.getKey())) {
                    targets.add(new TargetField(path, entry.getKey(), location,
                            ResourceSemanticNaming.valueShape(entry.getValue().getAsJsonPrimitive())));
                }
            } else if (ResourceValueExtractor.isPrimitiveArray(entry.getValue())) {
                JsonPrimitive firstElement = entry.getValue().getAsJsonArray().get(0).getAsJsonPrimitive();
                RequestTarget.Location location = targetLocation(data, entry.getKey(), pathParametersPayload);
                if (location == RequestTarget.Location.PATH || location == RequestTarget.Location.QUERY ||
                        ResourceSemanticNaming.isIdentifierName(entry.getKey())) {
                    targets.add(new TargetField(path, entry.getKey(), location,
                            ResourceSemanticNaming.valueShape(firstElement)));
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

    boolean hasExplicitValue(ServiceData data, TargetField target) {
        boolean hasRefData = filesArguments.getRefData(data.getRelativePath()).keySet().stream()
                .map(key -> key.replace('.', '#'))
                .anyMatch(key -> key.equalsIgnoreCase(target.path()));
        if (hasRefData) {
            return true;
        }
        if (target.location() == RequestTarget.Location.PATH && !filesArguments.isNotUrlParam(target.name())) {
            return true;
        }
        return target.location() == RequestTarget.Location.QUERY &&
                filesArguments.getAdditionalQueryParamsForPath(data.getRelativePath()).keySet().stream()
                        .anyMatch(key -> key.equalsIgnoreCase(target.name()));
    }

    boolean hasExplicitHeader(ServiceData data, String name) {
        return filesArguments.getHeaders(data.getRelativePath()).keySet().stream().anyMatch(name::equalsIgnoreCase);
    }

    boolean shouldSkipOwnPostIdentifier(ServiceData data, TargetField target) {
        if (data.getHttpMethod() != HttpMethod.POST || target.location() != RequestTarget.Location.BODY) {
            return false;
        }
        Set<String> targetAliases = ResourceSemanticNaming.semanticAliases(target.path(), target.name());
        boolean topLevelIdentifier = !target.path().contains("#") &&
                ResourceSemanticNaming.SIMPLE_IDENTIFIERS.contains(ResourceSemanticNaming.normalize(target.name()));
        return topLevelIdentifier ||
                ResourceSemanticNaming.primaryResourceIdentifierAliases(data.getRelativePath()).stream()
                        .anyMatch(targetAliases::contains);
    }

    Optional<ResolvedValue> resolve(String targetPath, TargetField target, ResourceInstance boundResource) {
        return resolve(targetPath, target, boundResource, false, null);
    }

    Optional<ResolvedValue> resolve(String targetPath, TargetField target, ResourceInstance boundResource,
                                    boolean boundOnly) {
        return resolve(targetPath, target, boundResource, boundOnly, null);
    }

    Optional<ResolvedValue> resolve(String targetPath, TargetField target, ResourceInstance boundResource,
                                    boolean boundOnly, HttpMethod targetMethod) {
        Set<String> targetAliases = ResourceSemanticNaming.targetAliases(targetPath, target);
        boolean destructivePathTarget = target.location() == RequestTarget.Location.PATH &&
                (targetMethod == HttpMethod.DELETE || targetMethod == HttpMethod.PUT || targetMethod == HttpMethod.PATCH);
        Set<String> protectedSelfValues = destructivePathTarget ? selfIdentityValues(targetPath) : Set.of();
        List<ResolvedValue> candidates = resources.stream()
                .filter(ResourceInstance::active)
                .filter(resource -> !destructivePathTarget || !isSelfIdentityGetResource(resource))
                .filter(resource -> !boundOnly || (boundResource != null &&
                        resource.sequence() == boundResource.sequence()))
                .flatMap(resource -> resource.values().values().stream()
                        .filter(value -> !protectedSelfValues.contains(String.valueOf(value.value())))
                        .filter(value -> value.aliases().stream().anyMatch(targetAliases::contains))
                        .map(value -> new ResolvedValue(resource, value,
                                ResourceSemanticNaming.pathAffinity(resource.sourcePath(), targetPath) * 1_000 +
                                        Math.min(50, ResourceSemanticNaming.matchSpecificity(value, targetAliases)) * 10 +
                                        sourcePriority(resource) + storedValuePriority(value) +
                                        feedbackScore(resource, targetPath, target))))
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

    Optional<String> alternativePathParameterValue(String targetPath, String parameterName, String currentValue) {
        if (StringUtils.isBlank(targetPath) || StringUtils.isBlank(parameterName) || StringUtils.isBlank(currentValue)) {
            return Optional.empty();
        }
        TargetField target = new TargetField(parameterName, parameterName, RequestTarget.Location.PATH,
                ResourceSemanticNaming.stringShape(currentValue));
        Set<String> targetAliases = ResourceSemanticNaming.targetAliases(targetPath, target);
        List<ResolvedValue> candidates = resources.stream()
                .filter(ResourceInstance::active)
                .filter(resource -> !resource.fuzzed())
                .filter(resource -> feedbackScore(resource, targetPath, target) >= 0)
                .flatMap(resource -> resource.values().values().stream()
                        .filter(value -> value.aliases().stream().anyMatch(targetAliases::contains))
                        .filter(value -> !ResourceSemanticNaming.sameValue(currentValue, value.value()))
                        .map(value -> new ResolvedValue(resource, value,
                                ResourceSemanticNaming.pathAffinity(resource.sourcePath(), targetPath) * 1_000 +
                                        Math.min(50, ResourceSemanticNaming.matchSpecificity(value, targetAliases)) * 10 +
                                        sourcePriority(resource) + storedValuePriority(value) +
                                        feedbackScore(resource, targetPath, target))))
                .filter(candidate -> ResourceSemanticNaming.semanticConfidence(candidate, targetPath, targetAliases) >= 2)
                .filter(candidate -> ResourceSemanticNaming.compatibilityQuality(candidate.storedValue().value(), target) > 0)
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

    ValueShape pathParameterShape(ServiceData data, String name) {
        for (String payload : List.of(Optional.ofNullable(data.getPathParamsPayload()).orElse(""),
                Optional.ofNullable(data.getPayload()).orElse(""))) {
            if (JsonUtils.isValidJson(payload)) {
                Object value = JsonUtils.getVariableFromJson(payload, name);
                if (!JsonUtils.isNotSet(String.valueOf(value))) {
                    return ResourceSemanticNaming.valueShape(value);
                }
            }
        }
        return ValueShape.STRING;
    }

    boolean wasModifiedByFuzzer(ServiceData data, String path, boolean pathParametersPayload) {
        if (pathParametersPayload || StringUtils.isBlank(data.getOriginalPayload()) ||
                !JsonUtils.isValidJson(data.getOriginalPayload())) {
            return false;
        }
        return !ResourceValueExtractor.valuesAtPath(data.getOriginalPayload(), path)
                .equals(ResourceValueExtractor.valuesAtPath(data.getPayload(), path));
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

    private boolean isSelfIdentityGetResource(ResourceInstance resource) {
        return resource.sourceMethod() == HttpMethod.GET &&
                ResourceSemanticNaming.SELF_IDENTITY_SEGMENTS.contains(
                        ResourceSemanticNaming.resourceName(resource.sourcePath()));
    }

    private Set<String> selfIdentityValues(String targetPath) {
        return resources.stream()
                .filter(this::isSelfIdentityGetResource)
                .filter(resource -> ResourceSemanticNaming.pathAffinity(resource.sourcePath(), targetPath) >= 2)
                .flatMap(resource -> resource.values().values().stream())
                .map(StoredValue::value)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toSet());
    }

    private List<ResolvedValue> childPathFallbackCandidates(String targetPath, ResourceInstance boundParentResource) {
        List<String> targetSegs = ResourceSemanticNaming.pathSegments(targetPath);
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
                    List<String> srcSegs = ResourceSemanticNaming.pathSegments(resource.sourcePath());
                    return srcSegs.size() >= 2 && srcSegs.getFirst().equalsIgnoreCase(rootSegment) &&
                            srcSegs.stream().anyMatch(ResourceSemanticNaming::isTemplateSegment);
                })
                .flatMap(resource -> resource.values().values().stream()
                        .filter(value -> value.aliases().contains("id"))
                        .filter(value -> !boundValues.contains(String.valueOf(value.value())))
                        .map(value -> new ResolvedValue(resource, value,
                                ResourceSemanticNaming.pathAffinity(resource.sourcePath(), targetPath) * 1_000 +
                                        sourcePriority(resource))))
                .toList();
    }

    private int candidateQuality(ResolvedValue candidate, String targetPath, TargetField target,
                                 Set<String> targetAliases, ResourceInstance boundResource) {
        int feedbackScore = feedbackScore(candidate.resource(), targetPath, target);
        int feedbackQuality = feedbackScore < 0 ? feedbackScore - MIN_FEEDBACK_PENALTY : -MIN_FEEDBACK_PENALTY;
        int compatibilityQuality = ResourceSemanticNaming.compatibilityQuality(candidate.storedValue().value(), target);
        int semanticConfidence = ResourceSemanticNaming.semanticConfidence(candidate, targetPath, targetAliases);
        int bindingQuality = boundResource != null && candidate.resource().sequence() == boundResource.sequence() ? 1 : 0;
        return feedbackQuality * 10_000 + compatibilityQuality * 1_000 + semanticConfidence * 100 +
                bindingQuality * 10 + (candidate.resource().fuzzed() ? 0 : 1);
    }

    private int feedbackScore(ResourceInstance resource, String targetPath, TargetField target) {
        return feedbackLookup.applyAsInt(resource, new FeedbackLookup(targetPath, target));
    }

    private int sourcePriority(ResourceInstance resource) {
        int methodPriority = resource.sourceMethod() == HttpMethod.POST ? 3 :
                resource.sourceMethod() == HttpMethod.PUT ? 2 : 1;
        return methodPriority * 2 + (resource.fuzzed() ? 0 : 1);
    }

    private int storedValuePriority(StoredValue value) {
        if (value.sourceLocation().startsWith("request.path.")) {
            return 3;
        }
        if (value.sourceLocation().startsWith("response.body.")) {
            String jsonPath = value.sourceLocation().substring("response.body.".length());
            String afterArrayRoot = jsonPath.replaceFirst("^\\$(?:\\.[^.\\[]+)?\\[\\]\\.", "\\$.");
            return ResourceSemanticNaming.isTopLevelResponseKey(afterArrayRoot) ? 2 : 1;
        }
        return value.sourceLocation().startsWith("response.") ? 1 : 0;
    }

    private boolean hasSiblingPostValue(ServiceData data, String fieldName) {
        if (data == null || data.getHttpMethod() != HttpMethod.POST) {
            return false;
        }
        List<String> targetSegments = ResourceSemanticNaming.pathSegments(data.getRelativePath());
        if (targetSegments.size() < 2) {
            return false;
        }
        String normalizedField = ResourceSemanticNaming.normalize(fieldName);
        return resources.stream()
                .filter(ResourceInstance::active)
                .filter(resource -> !resource.fuzzed() && resource.sourceMethod() == HttpMethod.POST)
                .filter(resource -> ResourceSemanticNaming.sameParentPath(
                        ResourceSemanticNaming.pathSegments(resource.sourcePath()), targetSegments))
                .flatMap(resource -> resource.values().values().stream())
                .filter(value -> value.sourceLocation().startsWith("request.body."))
                .anyMatch(value -> value.aliases().contains(normalizedField));
    }
}
