package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseTypeCoercionFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Fuzzer that sends numeric field values as strings with whitespace padding to check for type coercion issues.
 */
@Singleton
@FieldFuzzer
public class WhitespacePaddedNumericFieldsFuzzer extends BaseTypeCoercionFuzzer {
    /**
     * Creates a new WhitespacePaddedNumericFieldsFuzzer instance.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param processingArguments the processing arguments
     */
    public WhitespacePaddedNumericFieldsFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
        super(simpleExecutor, processingArguments);
    }

    @Override
    protected BiFunction<String, FuzzingData, Boolean> fieldsFilterFunction() {
        return (field, data) -> {
            Schema<?> schema = data.getRequestPropertyTypes().get(field);
            return CatsModelUtils.isNumberSchema(schema) || CatsModelUtils.isIntegerSchema(schema);
        };
    }

    @Override
    protected List<Object> getFuzzedValues(Object currentValue) {
        return List.of(" " + currentValue, "\t" + currentValue, "\n" + currentValue, "\r" + currentValue);
    }

    @Override
    protected String getOriginalType() {
        return "number";
    }

    @Override
    protected String getFuzzedType() {
        return "whitespace padded number";
    }
}
