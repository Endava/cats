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
 * Fuzzer that sends enum field values as strings to check for type coercion issues.
 */
@Singleton
@FieldFuzzer
public class EnumNumericOrdinalFieldsFuzzer extends BaseTypeCoercionFuzzer {
    /**
     * Creates a new EnumNumericOrdinalFieldsFuzzer instance.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param processingArguments the processing arguments
     */
    public EnumNumericOrdinalFieldsFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
        super(simpleExecutor, processingArguments);
    }

    @Override
    protected BiFunction<String, FuzzingData, Boolean> fieldsFilterFunction() {
        return (field, data) -> {
            Schema<?> schema = data.getRequestPropertyTypes().get(field);
            return CatsModelUtils.isEnumSchema(schema);
        };
    }

    @Override
    protected List<Object> getFuzzedValues(Object currentValue) {
        return List.of(0, 1);
    }

    @Override
    protected String getOriginalType() {
        return "enum";
    }

    @Override
    protected String getFuzzedType() {
        return "numeric enum ordinal";
    }
}
