package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.JsonUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sends objects/maps with fewer properties than the OpenAPI {@code minProperties} constraint ({@code minProperties - 1}).
 */
@Singleton
@FieldFuzzer
public class UnderflowMapSizeFieldsFuzzer extends BaseReplaceFieldsFuzzer {

    public UnderflowMapSizeFieldsFuzzer(FieldsIteratorExecutor executor) {
        super(executor);
    }

    @Override
    public BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsContext.builder()
                .replaceWhat("object/map with minProperties constraint")
                .replaceWith("underflow object/map")
                .skipMessage("Fuzzer only runs for objects/maps declaring minProperties > 0")
                .fieldFilter(field -> isEligibleMapField(data, field))
                .fuzzValueProducer((schema, field) -> List.of(buildUnderflowMap(data, schema, field)))
                .build();
    }

    private static boolean isEligibleMapField(FuzzingData data, String field) {
        Schema<?> schema = data.getRequestPropertyTypes().get(field);
        return schema != null
                && schema.getMinProperties() != null
                && schema.getMinProperties() > 0
                && JsonUtils.isValidMap(data.getPayload(), field);
    }

    static String buildUnderflowMap(FuzzingData data, Schema<?> schema, String field) {
        int targetSize = Math.max(0, schema.getMinProperties() - 1);
        if (targetSize == 0) {
            return "{}";
        }
        Map<String, Object> retained = new LinkedHashMap<>();
        Object rawKeys = JsonUtils.getVariableFromJson(data.getPayload(), field + ".keys()");
        if (rawKeys instanceof Set<?> keys) {
            keys.stream()
                    .map(String::valueOf)
                    .sorted()
                    .limit(targetSize)
                    .forEach(key -> retained.put(key, JsonUtils.getVariableFromJson(data.getPayload(), field + "." + key)));
        }
        return JsonUtils.GSON.toJson(retained);
    }
}
