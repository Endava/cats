package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseTypeCoercionFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Fuzzer that sends IEEE-754 special floating-point string tokens ({@code "NaN"}, {@code "Infinity"},
 * {@code "-Infinity"}) in decimal/number fields to detect lenient float-parser coercion and downstream
 * arithmetic, database, or JSON serialization crashes.
 */
@Singleton
@FieldFuzzer
public class SpecialFloatTokensInNumericFieldsFuzzer extends BaseTypeCoercionFuzzer {
    private static final List<Object> SPECIAL_FLOAT_TOKENS = List.of("NaN", "Infinity", "-Infinity");

    /**
     * Creates a new SpecialFloatTokensInNumericFieldsFuzzer instance.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param processingArguments the processing arguments
     */
    public SpecialFloatTokensInNumericFieldsFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
        super(simpleExecutor, processingArguments);
    }

    @Override
    protected BiFunction<String, FuzzingData, Boolean> fieldsFilterFunction() {
        return (field, data) -> {
            Schema<?> schema = data.getRequestPropertyTypes().get(field);
            return CatsModelUtils.isNumberSchema(schema) || CatsModelUtils.isFloatSchema(schema);
        };
    }

    @Override
    protected List<Object> getFuzzedValues(Object currentValue) {
        return SPECIAL_FLOAT_TOKENS;
    }

    @Override
    protected String getOriginalType() {
        return "number";
    }

    @Override
    protected String getFuzzedType() {
        return "IEEE-754 special float token";
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.TRACE);
    }
}
