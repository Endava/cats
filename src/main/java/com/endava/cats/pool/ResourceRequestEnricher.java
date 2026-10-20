package com.endava.cats.pool;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.WordUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
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

    record FeedbackLookup(String targetPath, TargetField target, String sourceLocation) {
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
        JsonElement parsedJson = parseJsonOrNull(json);
        if (parsedJson == null || (!parsedJson.isJsonObject() && !parsedJson.isJsonArray())) {
            if (!pathParametersPayload && HttpMethod.requiresBody(data.getHttpMethod())) {
                Optional<String> rootScalar = enrichRootScalarBody(json, data, correlations, correlationSources);
                if (rootScalar.isPresent()) {
                    return rootScalar.get();
                }
            }
            return json;
        }

        List<TargetField> targets = orderPathTargetsLeafFirst(
                reusableTargets(parsedJson, data, pathParametersPayload), data.getRelativePath());
        if (targets.isEmpty()) {
            return json;
        }
        boolean checkFuzzerDiff = !pathParametersPayload && StringUtils.isNotBlank(data.getOriginalPayload()) &&
                !data.getOriginalPayload().equals(data.getPayload());
        JsonElement originalRoot = checkFuzzerDiff ? parseJsonOrNull(data.getOriginalPayload()) : null;
        JsonElement payloadRoot = checkFuzzerDiff ? parseJsonOrNull(data.getPayload()) : null;

        String result = json;
        ResourceInstance[] pathBoundResource = new ResourceInstance[1];
        for (TargetField target : targets) {
            result = enrichTargetField(result, target, data, correlations, correlationSources,
                    requestBindings, pathBoundResource, originalRoot, payloadRoot);
        }
        return result;
    }

    private static JsonElement parseJsonOrNull(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        String trimmed = json.trim();
        char first = trimmed.charAt(0);
        if (first != '{' && first != '[') {
            return null;
        }
        try {
            return JsonUtils.parseAsJsonElement(trimmed);
        } catch (RuntimeException _) {
            return null;
        }
    }

    private String enrichTargetField(String currentJson, TargetField target, ServiceData data,
                                     List<ResourceCorrelation> correlations,
                                     Map<ResourceCorrelation, Long> correlationSources,
                                     Map<String, ResourceInstance> requestBindings,
                                     ResourceInstance[] pathBoundResource,
                                     JsonElement originalRoot, JsonElement payloadRoot) {
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
                (originalRoot != null && payloadRoot != null &&
                        !ResourceValueExtractor.valuesAtPath(originalRoot, target.path())
                                .equals(ResourceValueExtractor.valuesAtPath(payloadRoot, target.path())));
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
        JsonElement root = parseJsonOrNull(json);
        if (root == null) {
            return List.of();
        }
        return reusableTargets(root, data, pathParametersPayload);
    }

    private List<TargetField> reusableTargets(JsonElement root, ServiceData data, boolean pathParametersPayload) {
        List<TargetField> targets = new ArrayList<>();
        List<String> targetSegments = data != null && data.getHttpMethod() == HttpMethod.POST
                ? ResourceSemanticNaming.pathSegments(data.getRelativePath())
                : List.of();
        collectReusableTargets(root, "", data, pathParametersPayload, targetSegments, targets);
        return targets;
    }

    private void collectReusableTargets(JsonElement element, String prefix, ServiceData data,
                                        boolean pathParametersPayload, List<String> targetSegments,
                                        List<TargetField> targets) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (!array.isEmpty()) {
                collectReusableTargets(array.get(0), prefix, data, pathParametersPayload, targetSegments, targets);
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
                        ResourceSemanticNaming.isIdentifierName(entry.getKey()) ||
                        hasSiblingPostValue(targetSegments, entry.getKey()) ||
                        hasMatchingResourceCollection(entry.getKey())) {
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
                collectReusableTargets(entry.getValue(), path, data, pathParametersPayload, targetSegments, targets);
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
        ResourceSemanticNaming.TargetMatchContext matchCtx =
                ResourceSemanticNaming.createTargetMatchContext(targetPath, target, targetAliases);
        boolean destructivePathTarget = target.location() == RequestTarget.Location.PATH &&
                (targetMethod == HttpMethod.DELETE || targetMethod == HttpMethod.PUT || targetMethod == HttpMethod.PATCH);
        Set<String> protectedSelfValues = destructivePathTarget ? selfIdentityValues(matchCtx.targetPathSegments()) : Set.of();
        boolean canSkipByDirectAlias = matchCtx.digitMatchResource() == null && matchCtx.singleWordTargetPrefix() == null;

        ResolvedValue bestCandidate = null;
        int bestQuality = Integer.MIN_VALUE;

        for (ResourceInstance resource : resources) {
            if (!resource.active()) {
                continue;
            }
            if (boundOnly && (boundResource == null || resource.sequence() != boundResource.sequence())) {
                continue;
            }
            if (destructivePathTarget && isSelfIdentityGetResource(resource)) {
                continue;
            }
            if (canSkipByDirectAlias && !resource.hasAnyAlias(matchCtx.allDirectAliases())) {
                continue;
            }
            int affinityScore = ResourceSemanticNaming.pathAffinity(resource.pathSegments(), matchCtx.targetPathSegments()) * 1_000;
            int srcPriority = sourcePriority(resource);
            for (StoredValue value : resource.values().values()) {
                if (!protectedSelfValues.isEmpty() && protectedSelfValues.contains(String.valueOf(value.value()))) {
                    continue;
                }
                int similarity = matchedSimilarity(value, target, matchCtx);
                if (similarity == 0) {
                    continue;
                }
                int feedback = feedbackScore(resource, value, targetPath, target);
                int score = affinityScore +
                        similarity * 5 +
                        Math.min(50, ResourceSemanticNaming.matchSpecificity(value, targetAliases)) * 10 +
                        srcPriority + storedValuePriority(value) + feedback;
                ResolvedValue candidate = new ResolvedValue(resource, value, score);
                int quality = candidateQuality(candidate, similarity, feedback, target, matchCtx, boundResource);
                if (quality > bestQuality || (quality == bestQuality && isHigherRanked(candidate, bestCandidate))) {
                    bestQuality = quality;
                    bestCandidate = candidate;
                }
            }
        }
        if (bestCandidate == null && target.location() == RequestTarget.Location.PATH && !boundOnly) {
            for (ResolvedValue candidate : childPathFallbackCandidates(matchCtx.targetPathSegments(), boundResource)) {
                int similarity = ResourceSemanticNaming.nameSimilarity(candidate.storedValue(), matchCtx);
                int feedback = feedbackScore(candidate.resource(), candidate.storedValue(), targetPath, target);
                int quality = candidateQuality(candidate, similarity, feedback, target, matchCtx, boundResource);
                if (quality > bestQuality || (quality == bestQuality && isHigherRanked(candidate, bestCandidate))) {
                    bestQuality = quality;
                    bestCandidate = candidate;
                }
            }
        }
        return Optional.ofNullable(bestCandidate);
    }

    private static boolean isHigherRanked(ResolvedValue candidate, ResolvedValue currentBest) {
        if (currentBest == null) {
            return true;
        }
        if (candidate.score() != currentBest.score()) {
            return candidate.score() > currentBest.score();
        }
        return candidate.resource().sequence() > currentBest.resource().sequence();
    }

    Optional<String> alternativePathParameterValue(String targetPath, String parameterName, String currentValue) {
        if (StringUtils.isBlank(targetPath) || StringUtils.isBlank(parameterName) || StringUtils.isBlank(currentValue)) {
            return Optional.empty();
        }
        TargetField target = new TargetField(parameterName, parameterName, RequestTarget.Location.PATH,
                ResourceSemanticNaming.stringShape(currentValue));
        Set<String> targetAliases = ResourceSemanticNaming.targetAliases(targetPath, target);
        ResourceSemanticNaming.TargetMatchContext matchCtx =
                ResourceSemanticNaming.createTargetMatchContext(targetPath, target, targetAliases);
        boolean canSkipByDirectAlias = matchCtx.digitMatchResource() == null && matchCtx.singleWordTargetPrefix() == null;

        ResolvedValue bestCandidate = null;
        int bestQuality = Integer.MIN_VALUE;

        for (ResourceInstance resource : resources) {
            if (!resource.active() || resource.fuzzed()) {
                continue;
            }
            if (canSkipByDirectAlias && !resource.hasAnyAlias(matchCtx.allDirectAliases())) {
                continue;
            }
            if (feedbackScore(resource, null, targetPath, target) < 0) {
                continue;
            }
            int affinityScore = ResourceSemanticNaming.pathAffinity(resource.pathSegments(), matchCtx.targetPathSegments()) * 1_000;
            int srcPriority = sourcePriority(resource);
            for (StoredValue value : resource.values().values()) {
                int similarity = matchedSimilarity(value, target, matchCtx);
                if (similarity == 0 || ResourceSemanticNaming.compatibilityQuality(value, target) <= 0) {
                    continue;
                }
                int feedback = feedbackScore(resource, value, targetPath, target);
                if (feedback < 0 || ResourceSemanticNaming.sameValue(currentValue, value.value())) {
                    continue;
                }
                int score = affinityScore +
                        similarity * 5 +
                        Math.min(50, ResourceSemanticNaming.matchSpecificity(value, targetAliases)) * 10 +
                        srcPriority + storedValuePriority(value) + feedback;
                ResolvedValue candidate = new ResolvedValue(resource, value, score);
                if (ResourceSemanticNaming.semanticConfidence(candidate, similarity, matchCtx) < 2) {
                    continue;
                }
                int quality = candidateQuality(candidate, similarity, feedback, target, matchCtx, null);
                if (quality > bestQuality || (quality == bestQuality && isHigherRanked(candidate, bestCandidate))) {
                    bestQuality = quality;
                    bestCandidate = candidate;
                }
            }
        }
        return Optional.ofNullable(bestCandidate)
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
                data.getOriginalPayload().equals(data.getPayload())) {
            return false;
        }
        JsonElement originalRoot = parseJsonOrNull(data.getOriginalPayload());
        JsonElement payloadRoot = parseJsonOrNull(data.getPayload());
        if (originalRoot == null || payloadRoot == null) {
            return false;
        }
        return !ResourceValueExtractor.valuesAtPath(originalRoot, path)
                .equals(ResourceValueExtractor.valuesAtPath(payloadRoot, path));
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
                ResourceSemanticNaming.SELF_IDENTITY_SEGMENTS.contains(resource.resourceName());
    }

    private Set<String> selfIdentityValues(List<String> targetSegments) {
        return resources.stream()
                .filter(this::isSelfIdentityGetResource)
                .filter(resource -> ResourceSemanticNaming.pathAffinity(resource.pathSegments(), targetSegments) >= 2)
                .flatMap(resource -> resource.values().values().stream())
                .map(StoredValue::value)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toSet());
    }

    private List<ResolvedValue> childPathFallbackCandidates(List<String> targetSegs, ResourceInstance boundParentResource) {
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
                    List<String> srcSegs = resource.pathSegments();
                    return srcSegs.size() >= 2 && srcSegs.getFirst().equalsIgnoreCase(rootSegment) &&
                            srcSegs.stream().anyMatch(ResourceSemanticNaming::isTemplateSegment);
                })
                .flatMap(resource -> resource.values().values().stream()
                        .filter(value -> value.aliases().contains("id"))
                        .filter(value -> !boundValues.contains(String.valueOf(value.value())))
                        .map(value -> new ResolvedValue(resource, value,
                                ResourceSemanticNaming.pathAffinity(resource.pathSegments(), targetSegs) * 1_000 +
                                        sourcePriority(resource))))
                .toList();
    }

    private int matchedSimilarity(StoredValue value, TargetField target,
                                  ResourceSemanticNaming.TargetMatchContext matchCtx) {
        if (value.aliases().stream().anyMatch(matchCtx.targetAliases()::contains)) {
            return ResourceSemanticNaming.nameSimilarity(value, matchCtx);
        }
        if (value.aliases().stream().anyMatch(matchCtx.targetSingulars()::contains)) {
            return ResourceSemanticNaming.compatibilityQuality(value, target) > 0
                    ? ResourceSemanticNaming.nameSimilarity(value, matchCtx) : 0;
        }
        if (matchCtx.digitMatchResource() == null && matchCtx.singleWordTargetPrefix() == null) {
            return 0;
        }
        int similarity = ResourceSemanticNaming.nameSimilarity(value, matchCtx);
        return similarity >= 30 && ResourceSemanticNaming.compatibilityQuality(value, target) > 0 ? similarity : 0;
    }

    private int candidateQuality(ResolvedValue candidate, int similarity, int feedbackScore, TargetField target,
                                 ResourceSemanticNaming.TargetMatchContext matchCtx, ResourceInstance boundResource) {
        int feedbackQuality = feedbackScore < 0 ? feedbackScore - MIN_FEEDBACK_PENALTY : -MIN_FEEDBACK_PENALTY;
        int compatibilityQuality = ResourceSemanticNaming.compatibilityQuality(candidate.storedValue(), target);
        int semanticConfidence = ResourceSemanticNaming.semanticConfidence(candidate, similarity, matchCtx);
        int bindingQuality = boundResource != null && candidate.resource().sequence() == boundResource.sequence() ? 1 : 0;
        return feedbackQuality * 10_000 + compatibilityQuality * 1_000 + semanticConfidence * 100 +
                bindingQuality * 10 + (candidate.resource().fuzzed() ? 0 : 1);
    }

    private int feedbackScore(ResourceInstance resource, StoredValue value, String targetPath, TargetField target) {
        return feedbackLookup.applyAsInt(resource,
                new FeedbackLookup(targetPath, target, value != null ? value.sourceLocation() : null));
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

    private boolean hasSiblingPostValue(List<String> targetSegments, String fieldName) {
        if (targetSegments.size() < 2) {
            return false;
        }
        String normalizedField = ResourceSemanticNaming.normalize(fieldName);
        for (ResourceInstance resource : resources) {
            if (resource.active() && !resource.fuzzed() && resource.sourceMethod() == HttpMethod.POST &&
                    resource.requestBodyAliases().contains(normalizedField) &&
                    ResourceSemanticNaming.sameParentPath(resource.pathSegments(), targetSegments)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMatchingResourceCollection(String fieldName) {
        String normalizedField = ResourceSemanticNaming.normalize(fieldName);
        if (normalizedField.isEmpty() || ResourceSemanticNaming.SIMPLE_IDENTIFIERS.contains(normalizedField) ||
                ResourceSemanticNaming.isMetadataKey(fieldName)) {
            return false;
        }
        Set<String> fieldForms = WordUtils.nounForms(normalizedField);
        for (ResourceInstance resource : resources) {
            if (resource.active() && !resource.fuzzed() && resource.collectionPath() &&
                    !Collections.disjoint(resource.resourceNameForms(), fieldForms) &&
                    !Collections.disjoint(resource.allAliases(), fieldForms)) {
                return true;
            }
        }
        return false;
    }
}
