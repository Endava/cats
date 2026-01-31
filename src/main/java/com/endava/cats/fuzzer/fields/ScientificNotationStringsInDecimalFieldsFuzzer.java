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
 * Fuzzer that sends decimal field values as strings in scientific notation to check for type coercion issues.
 */
@Singleton
@FieldFuzzer
public class ScientificNotationStringsInDecimalFieldsFuzzer extends BaseTypeCoercionFuzzer {
    /**
     * Creates a new ScientificNotationStringsInDecimalFieldsFuzzer instance.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param processingArguments the processing arguments
     */
    public ScientificNotationStringsInDecimalFieldsFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
        super(simpleExecutor, processingArguments);
    }

    @Override
    protected BiFunction<String, FuzzingData, Boolean> fieldsFilterFunction() {
        return (field, data) -> {
            Schema<?> schema = data.getRequestPropertyTypes().get(field);
            return CatsModelUtils.isNumberSchema(schema);
        };
    }

    @Override
    protected List<Object> getFuzzedValues(Object currentValue) {
        String fromCurrentValue = new BigDecimal(String.valueOf(currentValue))
                .stripTrailingZeros()
                .toEngineeringString();

        return List.of(fromCurrentValue, "1.23e+10", "1.23e-10", "1.23e+100", "1.23e-100", "1.23e+1000", "1.23e-1000");
    }

    @Override
    protected String getOriginalType() {
        return "number";
    }

    @Override
    protected String getFuzzedType() {
        return "scientific notation string";
    }
}
