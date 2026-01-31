package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseTypeCoercionFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Fuzzer that sends numeric field values as strings to check for type coercion issues.
 */
@Singleton
@FieldFuzzer
public class NumericValuesInStringFieldsFuzzer extends BaseTypeCoercionFuzzer {
    /**
     * Creates a new NumericValuesInStringFieldsFuzzer instance.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param processingArguments the processing arguments
     */
    public NumericValuesInStringFieldsFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
        super(simpleExecutor, processingArguments);
    }

    @Override
    protected BiFunction<String, FuzzingData, Boolean> fieldsFilterFunction() {
        return (field, data) -> {
            Schema<?> schema = data.getRequestPropertyTypes().get(field);
            return CatsModelUtils.isStringSchema(schema);
        };
    }

    @Override
    protected List<Object> getFuzzedValues(Object currentValue) {
        return List.of(2, 200, 20.0122, new BigDecimal("9899"),
                Long.MAX_VALUE, Double.MAX_VALUE, Long.MIN_VALUE, Double.MIN_VALUE);
    }

    @Override
    protected String getOriginalType() {
        return "string";
    }

    @Override
    protected String getFuzzedType() {
        return "numeric";
    }
}
