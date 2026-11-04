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
import java.math.RoundingMode;
import java.util.List;

/**
 * Sends numeric values that stay within {@code [minimum, maximum]} bounds but violate
 * the OpenAPI {@code multipleOf} constraint.
 */
@Singleton
@FieldFuzzer
public class NonMultipleOfNumbersInNumericFieldsFuzzer extends BaseReplaceFieldsFuzzer {

    public NonMultipleOfNumbersInNumericFieldsFuzzer(FieldsIteratorExecutor executor) {
        super(executor);
    }

    @Override
    public BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsContext.builder()
                .replaceWhat("numeric with multipleOf constraint")
                .replaceWith("non-multiple numeric")
                .skipMessage("Fuzzer only runs for numeric fields declaring a violable multipleOf constraint")
                .fieldFilter(field -> isEligibleField(data, field))
                .fuzzValueProducer((schema, _) -> List.of(computeNonMultipleValue(schema)))
                .build();
    }

    private static boolean isEligibleField(FuzzingData data, String field) {
        Schema<?> schema = data.getRequestPropertyTypes().get(field);
        return hasViolableMultipleOf(schema) && JsonUtils.isFieldInJson(data.getPayload(), field);
    }

    static boolean hasViolableMultipleOf(Schema<?> schema) {
        if (schema == null || schema.getMultipleOf() == null || schema.getMultipleOf().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        boolean isInteger = CatsModelUtils.isIntegerSchema(schema);
        boolean isNumber = CatsModelUtils.isNumberSchema(schema);
        if (!isInteger && !isNumber) {
            return false;
        }
        return !isInteger || schema.getMultipleOf().compareTo(BigDecimal.ONE) > 0;
    }

    static Number computeNonMultipleValue(Schema<?> schema) {
        BigDecimal step = schema.getMultipleOf().stripTrailingZeros();
        boolean isInteger = CatsModelUtils.isIntegerSchema(schema);
        BigDecimal delta = isInteger
                ? BigDecimal.ONE
                : step.divide(BigDecimal.valueOf(2), Math.max(step.scale() + 2, 2), RoundingMode.HALF_UP).stripTrailingZeros();
        BigDecimal base = resolveBaseMultiple(schema, step);
        BigDecimal candidate = base.add(delta);
        if (schema.getMaximum() != null && candidate.compareTo(schema.getMaximum()) > 0) {
            candidate = base.subtract(delta);
        }
        return isInteger ? candidate.longValue() : candidate;
    }

    private static BigDecimal resolveBaseMultiple(Schema<?> schema, BigDecimal step) {
        if (schema.getMinimum() != null) {
            BigDecimal quotient = schema.getMinimum().divide(step, 0, RoundingMode.CEILING);
            return quotient.multiply(step);
        }
        return step;
    }
}
