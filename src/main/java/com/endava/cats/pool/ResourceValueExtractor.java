package com.endava.cats.pool;

import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.google.common.base.Splitter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Extracts reusable values, identifiers, and structured objects from HTTP URLs, headers, and JSON payloads.
 */
final class ResourceValueExtractor {
    static final int MAX_COLLECTION_ITEMS = 50;
    private static final Splitter FIELD_PATH_SPLITTER = Splitter.on('#').omitEmptyStrings();

    private ResourceValueExtractor() {
        // utility class
    }

    static Map<String, StoredValue> extractPathValues(String templatePath, String actualUrl) {
        if (StringUtils.isBlank(templatePath) || StringUtils.isBlank(actualUrl)) {
            return Map.of();
        }
        List<String> templateSegments = ResourceSemanticNaming.pathSegments(templatePath);
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
            if (ResourceSemanticNaming.isTemplateSegment(segment)) {
                String name = segment.substring(1, segment.length() - 1);
                String value = actualSegments.get(offset + i);
                values.put(name, new StoredValue(value, "request.path." + name, Set.of(ResourceSemanticNaming.normalize(name))));
            }
        }
        return values;
    }

    static Map<String, StoredValue> extractIdentifierQueryValues(ServiceData data, String actualUrl) {
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
                .filter(ResourceSemanticNaming::isIdentifierName)
                .forEach(name -> {
                    List<String> queryValues = url.queryParameterValues(name);
                    if (!queryValues.isEmpty()) {
                        String value = queryValues.getLast();
                        values.put(name, new StoredValue(value, "request.query." + name,
                                Set.of(ResourceSemanticNaming.normalize(name))));
                    }
                });
        return values;
    }

    static boolean collectResponseHeaders(CatsResponse response, Map<String, StoredValue> values) {
        int initialSize = values.size();
        for (KeyValuePair<String, String> header : Optional.ofNullable(response.getHeaders()).orElse(List.of())) {
            String normalized = ResourceSemanticNaming.normalize(header.getKey());
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

    private static boolean isReusableResponseHeader(String normalized) {
        return Set.of("etag", "location", "contentlocation", "operationlocation").contains(normalized) ||
                normalized.contains("version");
    }

    static Optional<StoredValue> locationIdentifier(CatsResponse response) {
        for (String name : List.of("Location", "Content-Location")) {
            KeyValuePair<String, String> locationHeader = response.getHeader(name);
            if (locationHeader == null || StringUtils.isBlank(locationHeader.getValue())) {
                continue;
            }
            String value = ResourceSemanticNaming.lastPathSegment(locationHeader.getValue());
            if (StringUtils.isNotBlank(value)) {
                return Optional.of(new StoredValue(value, "response.header." + name, Set.of("id")));
            }
        }
        return Optional.empty();
    }

    static Optional<StoredValue> wrapperIdentifier(Map<String, StoredValue> values) {
        List<Map.Entry<String, StoredValue>> topLevelEntries = values.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("$.") && ResourceSemanticNaming.isTopLevelResponseKey(entry.getKey()))
                .filter(entry -> entry.getValue() != null && entry.getValue().sourceLocation().startsWith("response.body."))
                .toList();
        List<Map.Entry<String, StoredValue>> candidates = topLevelEntries.stream()
                .filter(entry -> !ResourceSemanticNaming.isMetadataKey(ResourceSemanticNaming.normalize(entry.getKey())))
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

    static void collectKeyedMapIdentifier(String relativePath, JsonObject root, Map<String, StoredValue> values) {
        String resource = ResourceSemanticNaming.resourceName(relativePath);
        JsonElement mapCandidate = root.get(resource);
        if (mapCandidate != null && mapCandidate.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : mapCandidate.getAsJsonObject().entrySet()) {
                if (entry.getValue() != null && entry.getValue().isJsonObject() && StringUtils.isNotBlank(entry.getKey())) {
                    values.putIfAbsent("$." + resource + ".@key", new StoredValue(entry.getKey(),
                            "response.body.$." + resource + "." + entry.getKey(),
                            Set.of("id", "name", resource + "id", ResourceSemanticNaming.singular(resource) + "id")));
                    break;
                }
            }
        }
    }

    static boolean isScalarIdentifierBody(String value) {
        return !value.isEmpty() && value.length() <= 128 &&
                !value.startsWith("{") && !value.startsWith("[") && !value.startsWith("<") &&
                !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value) && !"null".equalsIgnoreCase(value) &&
                value.chars().noneMatch(Character::isWhitespace);
    }

    static boolean hasResourceIdentifier(Map<String, StoredValue> values, String path) {
        Set<String> aliases = new LinkedHashSet<>(ResourceSemanticNaming.SIMPLE_IDENTIFIERS);
        aliases.addAll(ResourceSemanticNaming.resourceIdentifierAliases(path));
        return values.values().stream()
                .filter(value -> value.sourceLocation().startsWith("response.body."))
                .anyMatch(value -> value.aliases().stream().anyMatch(aliases::contains));
    }

    static void collectPrimitiveValues(JsonObject object, String prefix, String sourcePrefix,
                                       Map<String, StoredValue> values) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().startsWith("_")) {
                continue;
            }
            String location = prefix + entry.getKey();
            if (entry.getValue().isJsonPrimitive()) {
                JsonPrimitive primitive = entry.getValue().getAsJsonPrimitive();
                Object value = ResourceSemanticNaming.primitiveValue(primitive);
                values.putIfAbsent(location, new StoredValue(value, sourcePrefix + location,
                        ResourceSemanticNaming.semanticAliases(location, entry.getKey())));
            } else if (entry.getValue().isJsonObject()) {
                collectPrimitiveValues(entry.getValue().getAsJsonObject(), location + ".", sourcePrefix, values);
            } else if (entry.getValue().isJsonArray()) {
                collectFirstArrayElementValues(entry.getValue().getAsJsonArray(), entry.getKey(), location, sourcePrefix, values);
            }
        }
    }

    private static void collectFirstArrayElementValues(JsonArray array, String key, String location, String sourcePrefix,
                                                       Map<String, StoredValue> values) {
        for (JsonElement item : array) {
            if (item != null && (item.isJsonObject() || item.isJsonPrimitive())) {
                if (item.isJsonObject()) {
                    collectPrimitiveValues(item.getAsJsonObject(), location + "[].", sourcePrefix, values);
                    List<JsonObject> arrayObjects = new ArrayList<>();
                    for (JsonElement el : array) {
                        if (el != null && el.isJsonObject() && arrayObjects.size() < MAX_COLLECTION_ITEMS) {
                            arrayObjects.add(el.getAsJsonObject());
                        }
                    }
                    inferObjectArrayKeyField(arrayObjects, key).ifPresent(keyProp ->
                            applyInferredKeyAlias(key, location + "[]." + keyProp, values));
                } else {
                    Object value = ResourceSemanticNaming.primitiveValue(item.getAsJsonPrimitive());
                    Set<String> aliases = new LinkedHashSet<>(ResourceSemanticNaming.semanticAliases(location, key));
                    aliases.add(ResourceSemanticNaming.singular(ResourceSemanticNaming.normalize(key)));
                    values.putIfAbsent(location + "[]", new StoredValue(value, sourcePrefix + location + "[]",
                            Set.copyOf(aliases)));
                }
                break;
            }
        }
    }

    static void applyInferredKeyAlias(String collectionOrArrayName, String keyLocation, Map<String, StoredValue> values) {
        StoredValue existing = values.get(keyLocation);
        String normCollection = ResourceSemanticNaming.normalize(collectionOrArrayName);
        if (existing == null || normCollection.isEmpty() ||
                ResourceSemanticNaming.COLLECTION_WRAPPER_SEGMENTS.contains(normCollection)) {
            return;
        }
        String singularCollection = ResourceSemanticNaming.singular(normCollection);
        if (!singularCollection.isEmpty()) {
            Set<String> aliases = new LinkedHashSet<>(existing.aliases());
            aliases.add(singularCollection);
            values.put(keyLocation, existing.withAliases(aliases));
        }
    }

    /**
     * Infers the natural key property of an array of objects when no explicit {@code id}/{@code uuid}/{@code guid}
     * or self-resource identifier field is present. Requires at least two object items so uniqueness can be verified
     * across items, and considers only string properties whose values are short (at most 32 characters), contain no
     * whitespace or path/scheme separators, include at least one ASCII alphanumeric character, and are distinct
     * across the observed items.
     * <p>
     * Picking the shortest unique string is a heuristic: when an item has multiple short unique string fields
     * (e.g. {@code {slug, title, email}}), it may initially select a non-key candidate, which outcome feedback
     * subsequently demotes if rejected by the service.
     */
    static Optional<String> inferObjectArrayKeyField(List<JsonObject> objects, String collectionOrArrayName) {
        if (objects == null || objects.size() < 2) {
            return Optional.empty();
        }
        String normCollection = ResourceSemanticNaming.normalize(collectionOrArrayName);
        if (ResourceSemanticNaming.COLLECTION_WRAPPER_SEGMENTS.contains(normCollection)) {
            return Optional.empty();
        }
        Set<String> ownIdAliases = new LinkedHashSet<>(ResourceSemanticNaming.SIMPLE_IDENTIFIERS);
        if (!normCollection.isEmpty()) {
            String singCollection = ResourceSemanticNaming.singular(normCollection);
            ResourceSemanticNaming.SIMPLE_IDENTIFIERS.forEach(suffix -> {
                ownIdAliases.add(normCollection + suffix);
                ownIdAliases.add(singCollection + suffix);
            });
        }
        JsonObject first = objects.getFirst();
        for (String prop : first.keySet()) {
            if (ownIdAliases.contains(ResourceSemanticNaming.normalize(prop))) {
                return Optional.empty();
            }
        }
        String bestProp = null;
        double bestAvgLen = Double.MAX_VALUE;
        for (String prop : first.keySet()) {
            String normProp = ResourceSemanticNaming.normalize(prop);
            if (normProp.isEmpty() || ResourceSemanticNaming.isItemMetadataKey(normProp)) {
                continue;
            }
            Set<String> distinct = new LinkedHashSet<>();
            int count = 0;
            int totalLen = 0;
            boolean valid = true;
            for (JsonObject obj : objects) {
                JsonElement el = obj.get(prop);
                if (el == null || el.isJsonNull()) {
                    continue;
                }
                if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                    valid = false;
                    break;
                }
                String str = el.getAsString();
                if (!isCandidateKeyValue(str)) {
                    valid = false;
                    break;
                }
                count++;
                totalLen += str.length();
                distinct.add(str);
            }
            if (!valid || count < 2 || distinct.size() < count) {
                continue;
            }
            double avgLen = (double) totalLen / count;
            if (avgLen < bestAvgLen) {
                bestAvgLen = avgLen;
                bestProp = prop;
            }
        }
        return Optional.ofNullable(bestProp);
    }

    private static boolean isCandidateKeyValue(String str) {
        if (str == null || str.isEmpty() || str.length() > 32) {
            return false;
        }
        boolean hasAsciiAlnum = false;
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (Character.isWhitespace(ch) || ch == '/' || ch == ':') {
                return false;
            }
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                hasAsciiAlnum = true;
            }
        }
        return hasAsciiAlnum;
    }

    static List<ResponseObject> responseObjects(String body, boolean collectionOnly) {
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

    private static void collectNestedArrayObjects(JsonObject object, String prefix, List<ResponseObject> objects) {
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

    private static boolean containsIdentifierOutsideArrays(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue().isJsonPrimitive() && ResourceSemanticNaming.isIdentifierName(entry.getKey())) {
                return true;
            }
            if (entry.getValue().isJsonObject() && containsIdentifierOutsideArrays(entry.getValue().getAsJsonObject())) {
                return true;
            }
        }
        return false;
    }

    private static void addObjectElements(JsonArray array, String sourcePrefix, List<ResponseObject> objects) {
        for (JsonElement element : array) {
            if (element.isJsonObject() && objects.size() < MAX_COLLECTION_ITEMS) {
                objects.add(new ResponseObject(element.getAsJsonObject(), sourcePrefix));
            }
        }
    }

    static boolean isCollectionResponse(String body) {
        if (!JsonUtils.isValidJson(body)) {
            return false;
        }
        JsonElement root = JsonUtils.parseAsJsonElement(body);
        return root.isJsonArray() || (root.isJsonObject() && containsArray(root.getAsJsonObject()));
    }

    private static boolean containsArray(JsonObject object) {
        return object.entrySet().stream().anyMatch(entry -> entry.getValue().isJsonArray() ||
                (entry.getValue().isJsonObject() && containsArray(entry.getValue().getAsJsonObject())));
    }

    static List<JsonObject> requestObjects(String payload) {
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

    static boolean alignedObjectPayloads(String requestPayload, String responseBody,
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

    private static boolean containsOnlyObservedObjects(JsonArray array, int expectedObjects) {
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

    static List<JsonElement> valuesAtPath(String json, String path) {
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

    private static void collectValuesForSegment(JsonElement element, String segment, List<JsonElement> values) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> collectValuesForSegment(item, segment, values));
        } else if (element.isJsonObject() && element.getAsJsonObject().has(segment)) {
            values.add(element.getAsJsonObject().get(segment));
        }
    }

    static boolean isPrimitiveArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return false;
        }
        JsonArray array = element.getAsJsonArray();
        return !array.isEmpty() && array.get(0) != null && array.get(0).isJsonPrimitive();
    }

    static boolean isRequestBodyFuzzed(ServiceData data) {
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

    static boolean isFuzzedRequest(ServiceData data) {
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
}
