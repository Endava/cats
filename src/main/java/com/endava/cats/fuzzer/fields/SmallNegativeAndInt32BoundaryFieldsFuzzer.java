package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseTypeCoercionFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Fuzzer that sends {@code 0}, {@code -1}, and {@code Integer.MAX_VALUE} in integer and numeric fields
 * (including query parameters on GET requests) so values pass 32-bit integer deserialization and reach
 * controller and service boundary checks.
 */
@Singleton
@FieldFuzzer
public class SmallNegativeAndInt32BoundaryFieldsFuzzer extends BaseTypeCoercionFuzzer {
    /**
     * Creates a new SmallNegativeAndInt32BoundaryFieldsFuzzer instance.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param processingArguments the processing arguments
     */
    public SmallNegativeAndInt32BoundaryFieldsFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
        super(simpleExecutor, processingArguments);
    }

    @Override
    protected BiFunction<String, FuzzingData, Boolean> fieldsFilterFunction() {
        return (field, data) -> {
            Schema<?> schema = data.getRequestPropertyTypes().get(field);
            return CatsModelUtils.isIntegerSchema(schema) || CatsModelUtils.isNumberSchema(schema);
        };
    }

    @Override
    protected List<Object> getFuzzedValues(Object currentValue) {
        return List.of(0, -1, Integer.MAX_VALUE);
    }

    @Override
    protected ResponseCodeFamily expectedResponseCode(FuzzingData data, String field, Object fuzzedValue) {
        if (!getProcessingArguments().isStrictTypes()) {
            return ResponseCodeFamilyPredefined.FOURXX_TWOXX;
        }
        Schema<?> schema = data.getRequestPropertyTypes() == null ? null : data.getRequestPropertyTypes().get(field);
        return violatesSchemaBounds(schema, fuzzedValue)
                ? ResponseCodeFamilyPredefined.FOURXX
                : ResponseCodeFamilyPredefined.FOURXX_TWOXX;
    }

    private static boolean violatesSchemaBounds(Schema<?> schema, Object value) {
        if (schema == null || !(value instanceof Number number)) {
            return false;
        }
        BigDecimal candidate = new BigDecimal(number.toString());
        if (schema.getMinimum() != null) {
            int cmp = candidate.compareTo(schema.getMinimum());
            if (Boolean.TRUE.equals(schema.getExclusiveMinimum()) ? cmp <= 0 : cmp < 0) {
                return true;
            }
        }
        if (schema.getExclusiveMinimumValue() != null && candidate.compareTo(schema.getExclusiveMinimumValue()) <= 0) {
            return true;
        }
        if (schema.getMaximum() != null) {
            int cmp = candidate.compareTo(schema.getMaximum());
            if (Boolean.TRUE.equals(schema.getExclusiveMaximum()) ? cmp >= 0 : cmp > 0) {
                return true;
            }
        }
        return schema.getExclusiveMaximumValue() != null && candidate.compareTo(schema.getExclusiveMaximumValue()) >= 0;
    }

    @Override
    protected String getOriginalType() {
        return "integer";
    }

    @Override
    protected String getFuzzedType() {
        return "zero, -1, and int32 max boundary";
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.TRACE);
    }
}
