package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Violates OpenAPI 3.1 {@code const} constraints (by sending a different value of the same type)
 * and OpenAPI 3.0/3.1 {@code not} schemas (by sending a value matching the forbidden {@code not} schema).
 */
@Singleton
@FieldFuzzer
public class ConstAndNotSchemaFieldsFuzzer extends BaseReplaceFieldsFuzzer {

    public ConstAndNotSchemaFieldsFuzzer(FieldsIteratorExecutor executor) {
        super(executor);
    }

    @Override
    public BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsContext.builder()
                .replaceWhat("field with const or not schema constraint")
                .replaceWith("value violating const or matching not schema")
                .skipMessage("Fuzzer only runs for fields declaring const or not schema constraints")
                .fieldFilter(field -> isEligibleField(data, field))
                .fuzzValueProducer((schema, _) -> buildViolatingValues(schema, data.getSchemaMap()))
                .build();
    }

    private static boolean isEligibleField(FuzzingData data, String field) {
        Schema<?> schema = data.getRequestPropertyTypes().get(field);
        return !buildViolatingValues(schema, data.getSchemaMap()).isEmpty()
                && JsonUtils.isFieldInJson(data.getPayload(), field);
    }

    static List<Object> buildViolatingValues(Schema<?> schema, Map<String, Schema> schemaMap) {
        if (schema == null) {
            return List.of();
        }
        List<Object> values = new ArrayList<>(2);
        if (schema.getConst() != null) {
            values.add(mutateConstValue(schema.getConst()));
        }
        if (schema.getNot() != null) {
            Schema<?> notSchema = CatsModelUtils.resolveSchema(schema.getNot(), schemaMap);
            Object matchingNot = synthesizeValueMatchingNotSchema(notSchema);
            if (matchingNot != null) {
                values.add(matchingNot);
            }
        }
        return List.copyOf(values);
    }

    static Object mutateConstValue(Object constValue) {
        return switch (constValue) {
            case Number number -> number.longValue() + 1L;
            case Boolean bool -> !bool;
            default -> constValue + "_violatedConst";
        };
    }

    static Object synthesizeValueMatchingNotSchema(Schema<?> notSchema) {
        if (notSchema == null) {
            return null;
        }
        if (notSchema.getConst() != null) {
            return notSchema.getConst();
        }
        if (CatsUtil.isNotEmpty(notSchema.getEnum())) {
            return notSchema.getEnum().getFirst();
        }
        if (notSchema.getDefault() != null) {
            return notSchema.getDefault();
        }
        if (notSchema.getExample() != null) {
            return notSchema.getExample();
        }
        if (notSchema.getMinimum() != null) {
            return CatsModelUtils.isIntegerSchema(notSchema)
                    ? notSchema.getMinimum().longValue()
                    : notSchema.getMinimum();
        }
        return synthesizeFallbackByType(notSchema);
    }

    private static Object synthesizeFallbackByType(Schema<?> notSchema) {
        if (CatsModelUtils.isBooleanSchema(notSchema)) {
            return true;
        }
        if (CatsModelUtils.isIntegerSchema(notSchema) || CatsModelUtils.isNumberSchema(notSchema)) {
            return 0;
        }
        if (CatsModelUtils.isStringSchema(notSchema)) {
            int len = notSchema.getMinLength() != null ? Math.max(1, notSchema.getMinLength()) : 4;
            return "a".repeat(Math.min(len, 64));
        }
        return null;
    }
}
