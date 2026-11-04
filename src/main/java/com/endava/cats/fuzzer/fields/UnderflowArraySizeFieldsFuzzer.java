package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.JsonUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Sends arrays with fewer elements than the OpenAPI {@code minItems} constraint ({@code minItems - 1}).
 */
@Singleton
@FieldFuzzer
public class UnderflowArraySizeFieldsFuzzer extends BaseReplaceFieldsFuzzer {

    public UnderflowArraySizeFieldsFuzzer(FieldsIteratorExecutor executor) {
        super(executor);
    }

    @Override
    public BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsContext.builder()
                .replaceWhat("array with minItems constraint")
                .replaceWith("underflow array")
                .skipMessage("Fuzzer only runs for arrays declaring minItems > 0")
                .fieldFilter(field -> isEligibleArrayField(data, field))
                .fuzzValueProducer((schema, field) -> List.of(buildUnderflowArray(data, schema, field)))
                .build();
    }

    private static boolean isEligibleArrayField(FuzzingData data, String field) {
        Schema<?> schema = data.getRequestPropertyTypes().get(field);
        return schema != null
                && schema.getMinItems() != null
                && schema.getMinItems() > 0
                && JsonUtils.isArray(data.getPayload(), field);
    }

    static String buildUnderflowArray(FuzzingData data, Schema<?> schema, String field) {
        int targetSize = Math.max(0, schema.getMinItems() - 1);
        if (targetSize == 0) {
            return "[]";
        }
        String firstElement = JsonUtils.serialize(JsonUtils.getVariableFromJson(data.getPayload(), field + "[0]"));
        String elementJson = Optional.ofNullable(firstElement).orElse("\"catsItem\"");
        return "[" + StringUtils.repeat(elementJson, ",", targetSize) + "]";
    }
}
