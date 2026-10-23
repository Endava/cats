package com.endava.cats.pool;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Stores and merges successful request payload baselines per operation, HTTP method, content type, and schema variant.
 */
final class SuccessfulBaselineStore {
    private static final int MAX_SUCCESSFUL_BASELINES = 500;
    private final Map<SuccessfulBaselineKey, String> baselines = new LinkedHashMap<>();

    void clear() {
        baselines.clear();
    }

    String apply(ServiceData data, boolean reuseEnabled) {
        String currentPayload = data.getPayload();
        boolean supportsStructured = data.getPayloadFormat().supportsNamedFields()
                || (com.endava.cats.io.XmlPayloadEncoder.isXmlContentType(data.getContentType()) && data.getReqSchema() != null);
        if (!reuseEnabled || !data.isReplaceRefData() || !data.isValidJson() ||
                !supportsStructured || !JsonUtils.isValidJson(currentPayload)) {
            return currentPayload;
        }
        Optional<SuccessfulBaselineKey> key = baselineKey(data);
        String baseline = key.map(baselines::get).orElse(null);
        String originalPayload = originalPayload(data);
        if (!JsonUtils.isValidJson(originalPayload) || !JsonUtils.isValidJson(baseline)) {
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

    void store(ServiceData data, String effectivePayload) {
        boolean requiredOnlyFallback = data.getAllMutationTargets().equals(List.of(RequestTarget.requestBody())) ||
                data.getAllMutationTargets().equals(List.of(RequestTarget.query("Optional parameters")));
        boolean fieldRemovalFallback = isSubsetOfOriginalPayload(data, effectivePayload);
        boolean fallbackBaseline = requiredOnlyFallback || fieldRemovalFallback;
        boolean supportsStructured = data.getPayloadFormat().supportsNamedFields()
                || (com.endava.cats.io.XmlPayloadEncoder.isXmlContentType(data.getContentType()) && data.getReqSchema() != null);
        if ((ResourceValueExtractor.isRequestBodyFuzzed(data) && !fallbackBaseline) || !data.isReplaceRefData() || !data.isValidJson() ||
                !supportsStructured ||
                (!HttpMethod.requiresBody(data.getHttpMethod()) && !fallbackBaseline) ||
                !JsonUtils.isValidJson(effectivePayload)) {
            return;
        }
        baselineKey(data).ifPresent(key -> {
            if (fallbackBaseline && baselines.containsKey(key)) {
                return;
            }
            baselines.put(key, JsonUtils.parseAsJsonElement(effectivePayload).toString());
            while (baselines.size() > MAX_SUCCESSFUL_BASELINES) {
                baselines.remove(baselines.keySet().iterator().next());
            }
        });
    }

    void evictContainingPrimitiveValues(Set<String> deletedPrimitiveValues) {
        baselines.entrySet().removeIf(entry -> containsPrimitiveValue(entry.getValue(), deletedPrimitiveValues));
    }

    private boolean isSubsetOfOriginalPayload(ServiceData data, String effectivePayload) {
        String originalPayload = originalPayload(data);
        if (!JsonUtils.isValidJson(originalPayload) || !JsonUtils.isValidJson(effectivePayload)) {
            return false;
        }
        JsonElement origEl = JsonUtils.parseAsJsonElement(originalPayload);
        JsonElement effEl = JsonUtils.parseAsJsonElement(effectivePayload);
        if (origEl == null || effEl == null || !origEl.isJsonObject() || !effEl.isJsonObject()) {
            return false;
        }
        JsonObject origObj = origEl.getAsJsonObject();
        JsonObject effObj = effEl.getAsJsonObject();
        return effObj.size() < origObj.size() && origObj.keySet().containsAll(effObj.keySet());
    }

    private Optional<SuccessfulBaselineKey> baselineKey(ServiceData data) {
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
            if (original.equals(successful)) {
                return current.deepCopy();
            }
            Object currentVal = ResourceSemanticNaming.primitiveValue(current.getAsJsonPrimitive());
            Object origVal = original.isJsonPrimitive()
                    ? ResourceSemanticNaming.primitiveValue(original.getAsJsonPrimitive())
                    : null;
            if (origVal != null && !String.valueOf(FuzzingStrategy.mergeFuzzing(currentVal, origVal)).equals(String.valueOf(currentVal))) {
                return current.deepCopy();
            }
            Object merged = FuzzingStrategy.mergeFuzzing(
                    currentVal,
                    ResourceSemanticNaming.primitiveValue(successful.getAsJsonPrimitive()));
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
            return (fieldName == null || ResourceSemanticNaming.isIdentifierName(fieldName)) &&
                    values.stream().anyMatch(deleted -> ResourceSemanticNaming.sameValue(
                            deleted, ResourceSemanticNaming.primitiveValue(element.getAsJsonPrimitive())));
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().asList().stream()
                    .anyMatch(item -> containsPrimitiveValue(item, fieldName, values));
        }
        return element.getAsJsonObject().entrySet().stream()
                .anyMatch(entry -> containsPrimitiveValue(entry.getValue(), entry.getKey(), values));
    }

    private record SuccessfulBaselineKey(String path, HttpMethod method, String contentType, String variant) {
    }
}
