package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.JsonUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends the exact excluded boundary value on numeric fields declaring OpenAPI 3.0
 * {@code exclusiveMinimum: true} / {@code exclusiveMaximum: true} or OpenAPI 3.1
 * {@code exclusiveMinimum} / {@code exclusiveMaximum} numeric bounds, expecting 4XX.
 */
@Singleton
@FieldFuzzer
public class ExclusiveBoundaryNumbersInNumericFieldsFuzzer extends BaseReplaceFieldsFuzzer {

    public ExclusiveBoundaryNumbersInNumericFieldsFuzzer(FieldsIteratorExecutor executor) {
        super(executor);
    }

    @Override
    public BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsContext.builder()
                .replaceWhat("numeric with exclusiveMinimum/exclusiveMaximum")
                .replaceWith("exact excluded boundary")
                .skipMessage("Fuzzer only runs for numeric fields with exclusiveMinimum or exclusiveMaximum")
                .fieldFilter(field -> isEligibleField(data, field))
                .fuzzValueProducer((schema, _) -> extractExcludedBoundaries(schema))
                .build();
    }

    private static boolean isEligibleField(FuzzingData data, String field) {
        Schema<?> schema = data.getRequestPropertyTypes().get(field);
        return !extractExcludedBoundaries(schema).isEmpty()
                && JsonUtils.isFieldInJson(data.getPayload(), field);
    }

    static List<Object> extractExcludedBoundaries(Schema<?> schema) {
        if (schema == null || (!CatsModelUtils.isIntegerSchema(schema) && !CatsModelUtils.isNumberSchema(schema))) {
            return List.of();
        }
        boolean isInteger = CatsModelUtils.isIntegerSchema(schema);
        List<Object> excluded = new ArrayList<>(2);
        addIfPresent(excluded, resolveExclusiveMin(schema), isInteger);
        addIfPresent(excluded, resolveExclusiveMax(schema), isInteger);
        return List.copyOf(excluded);
    }

    private static BigDecimal resolveExclusiveMin(Schema<?> schema) {
        if (schema.getExclusiveMinimumValue() != null) {
            return schema.getExclusiveMinimumValue();
        }
        return Boolean.TRUE.equals(schema.getExclusiveMinimum()) ? schema.getMinimum() : null;
    }

    private static BigDecimal resolveExclusiveMax(Schema<?> schema) {
        if (schema.getExclusiveMaximumValue() != null) {
            return schema.getExclusiveMaximumValue();
        }
        return Boolean.TRUE.equals(schema.getExclusiveMaximum()) ? schema.getMaximum() : null;
    }

    private static void addIfPresent(List<Object> target, BigDecimal bound, boolean isInteger) {
        if (bound != null) {
            target.add(isInteger ? bound.longValue() : bound);
        }
    }
}
