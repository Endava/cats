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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Passively records resource identifiers returned by successful requests and reuses them in later requests.
 * No additional service calls are made by this component.
 */
@ApplicationScoped
public class RuntimeResourcePool {
    private static final int MAX_RESOURCES = 500;
    private static final int MAX_COLLECTION_ITEMS = 50;
    private static final Pattern SEPARATED_ID = Pattern.compile("(?i).*(?:_|-)(?:id|uuid|guid)$");
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
        String enrichedPayload = enrichJson(processedPayload, data, correlations, false);
        String enrichedPathParams = enrichJson(data.getPathParamsPayload(), data, correlations, true);

        return new ResolvedRequest(enrichedPayload, enrichedPathParams, List.copyOf(correlations));
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
        if (!processingArguments.isReuseSuccessfulResources() || !ResponseCodeFamily.is2xxCode(response.getResponseCode())) {
            return;
        }

        if (data.getHttpMethod() == HttpMethod.DELETE) {
            markDeleted(data.getRelativePath(), request.getUrl());
            return;
        }

        if (data.getHttpMethod() != HttpMethod.POST && data.getHttpMethod() != HttpMethod.PUT && data.getHttpMethod() != HttpMethod.GET) {
            return;
        }

        Map<String, StoredValue> pathValues = extractPathValues(data.getRelativePath(), request.getUrl());
        boolean collectionGet = data.getHttpMethod() == HttpMethod.GET;
        if (collectionGet && (!isCollectionPath(data.getRelativePath()) || !isCollectionResponse(response.getBody()))) {
            return;
        }
        List<ResponseObject> responseObjects = responseObjects(response.getBody(), collectionGet);

        if (responseObjects.isEmpty()) {
            addFromLocation(data, response, pathValues);
            return;
        }

        Optional<StoredValue> locationIdentifier = locationIdentifier(response);
        int limit = Math.min(responseObjects.size(), MAX_COLLECTION_ITEMS);
        for (int i = 0; i < limit; i++) {
            Map<String, StoredValue> values = new LinkedHashMap<>(pathValues);
            ResponseObject responseObject = responseObjects.get(i);
            collectPrimitiveValues(responseObject.value(), responseObject.sourcePrefix(), values);
            if (!hasResourceIdentifier(values, data.getRelativePath())) {
                locationIdentifier.ifPresent(value -> values.put("id", value));
            }
            addResource(data, values);
        }
    }

    /** Clears all values captured by the current run. */
    public synchronized void clear() {
        resources.clear();
        sequence.set(0);
    }

    private String enrichJson(String json, ServiceData data, List<ResourceCorrelation> correlations, boolean pathParametersPayload) {
        if (!JsonUtils.isValidJson(json)) {
            return json;
        }

        String result = json;
        for (TargetField target : reusableTargets(json, data, pathParametersPayload)) {
            if ((target.location() == RequestTarget.Location.PATH && !data.isReplaceUrlParams()) ||
                    hasExplicitValue(data, target) || shouldSkipOwnPostIdentifier(data, target)) {
                continue;
            }

            Optional<ResolvedValue> resolved = resolve(data.getRelativePath(), target);
            if (resolved.isEmpty()) {
                continue;
            }

            String before = result;
            ResolvedValue value = resolved.get();
            boolean mergeFuzzing = data.isFuzzedField(target.path(), target.location()) ||
                    wasModifiedByFuzzer(data, json, target.path(), pathParametersPayload);
            try {
                FuzzingResult fuzzingResult = FuzzingStrategy.replaceField(result, target.path(),
                        FuzzingStrategy.replace().withData(value.value()), mergeFuzzing);
                result = fuzzingResult.json();
            } catch (RuntimeException _) {
                continue;
            }

            if (!before.equals(result)) {
                correlations.add(ResourceCorrelation.builder()
                        .sourceMethod(value.resource().sourceMethod().name())
                        .sourcePath(value.resource().sourcePath())
                        .sourceLocation(value.storedValue().sourceLocation())
                        .target(new RequestTarget(target.location(), target.path()))
                        .value(value.value())
                        .build());
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
                if (location == RequestTarget.Location.PATH || isIdentifierName(entry.getKey())) {
                    targets.add(new TargetField(path, entry.getKey(), location));
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

    private Optional<ResolvedValue> resolve(String targetPath, TargetField target) {
        Set<String> targetAliases = semanticAliases(target.path(), target.name());
        return resources.stream()
                .filter(ResourceInstance::active)
                .flatMap(resource -> resource.values().values().stream()
                        .filter(value -> value.aliases().stream().anyMatch(targetAliases::contains))
                        .map(value -> new ResolvedValue(resource, value,
                                pathAffinity(resource.sourcePath(), targetPath) * 1_000 +
                                        Math.min(50, matchSpecificity(value, targetAliases)) * 10 +
                                        sourcePriority(resource) + storedValuePriority(value))))
                .max(Comparator.comparingInt(ResolvedValue::score)
                        .thenComparingLong(value -> value.resource().sequence()));
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
        return value.sourceLocation().startsWith("request.path.") ? 1 : 0;
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
            resources.remove(0);
        }
    }

    private void addFromLocation(ServiceData data, CatsResponse response, Map<String, StoredValue> pathValues) {
        Optional<StoredValue> locationIdentifier = locationIdentifier(response);
        if (locationIdentifier.isEmpty()) {
            if (!pathValues.isEmpty()) {
                addResource(data, pathValues);
            }
            return;
        }
        Map<String, StoredValue> values = new LinkedHashMap<>(pathValues);
        values.put("id", locationIdentifier.get());
        addResource(data, values);
    }

    private Optional<StoredValue> locationIdentifier(CatsResponse response) {
        KeyValuePair<String, String> locationHeader = response.getHeader("Location");
        if (locationHeader == null || StringUtils.isBlank(locationHeader.getValue())) {
            return Optional.empty();
        }
        String value = lastPathSegment(locationHeader.getValue());
        return StringUtils.isBlank(value) ? Optional.empty() :
                Optional.of(new StoredValue(value, "response.header.Location", Set.of("id")));
    }

    private boolean hasResourceIdentifier(Map<String, StoredValue> values, String path) {
        Set<String> aliases = new LinkedHashSet<>(SIMPLE_IDENTIFIERS);
        aliases.addAll(resourceIdentifierAliases(path));
        return values.values().stream()
                .filter(value -> value.sourceLocation().startsWith("response.body."))
                .anyMatch(value -> value.aliases().stream().anyMatch(aliases::contains));
    }

    private void markDeleted(String templatePath, String actualUrl) {
        Map<String, StoredValue> deletedValues = extractPathValues(templatePath, actualUrl);
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
            }
        }
    }

    private Map<String, StoredValue> extractPathValues(String templatePath, String actualUrl) {
        if (StringUtils.isBlank(templatePath) || StringUtils.isBlank(actualUrl)) {
            return Map.of();
        }
        List<String> templateSegments = pathSegments(templatePath);
        List<String> actualSegments;
        try {
            actualSegments = HttpUrl.get(actualUrl).pathSegments();
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

    private void collectPrimitiveValues(JsonObject object, String prefix, Map<String, StoredValue> values) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String location = prefix + entry.getKey();
            if (entry.getValue().isJsonPrimitive()) {
                JsonPrimitive primitive = entry.getValue().getAsJsonPrimitive();
                Object value = primitiveValue(primitive);
                values.putIfAbsent(location, new StoredValue(value, "response.body." + location,
                        semanticAliases(location, entry.getKey())));
            } else if (entry.getValue().isJsonObject()) {
                collectPrimitiveValues(entry.getValue().getAsJsonObject(), location + ".", values);
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

    private boolean wasModifiedByFuzzer(ServiceData data, String currentPayload, String path,
                                        boolean pathParametersPayload) {
        if (pathParametersPayload || StringUtils.isBlank(data.getOriginalPayload()) ||
                !JsonUtils.isValidJson(data.getOriginalPayload())) {
            return false;
        }
        return !valuesAtPath(data.getOriginalPayload(), path).equals(valuesAtPath(currentPayload, path));
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
            List<String> segments = HttpUrl.get(location).pathSegments();
            return segments.isEmpty() ? "" : segments.getLast();
        } catch (IllegalArgumentException _) {
            String withoutQuery = location.contains("?") ? location.substring(0, location.indexOf('?')) : location;
            return withoutQuery.substring(withoutQuery.lastIndexOf('/') + 1);
        }
    }

    private String normalize(String value) {
        return NON_ALPHANUMERIC.matcher(Optional.ofNullable(value).orElse(""))
                .replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** Result of applying runtime resource correlations to request data. */
    public record ResolvedRequest(String payload, String pathParamsPayload, List<ResourceCorrelation> correlations) {
    }

    private record TargetField(String path, String name, RequestTarget.Location location) {
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
