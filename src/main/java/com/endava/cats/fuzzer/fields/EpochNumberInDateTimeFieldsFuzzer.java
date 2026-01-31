package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseTypeCoercionFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Fuzzer that sends epoch number field values as strings to check for type coercion issues.
 */
@Singleton
@FieldFuzzer
public class EpochNumberInDateTimeFieldsFuzzer extends BaseTypeCoercionFuzzer {
    /**
     * Creates a new EpochNumberInDateTimeFieldsFuzzer instance.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param processingArguments the processing arguments
     */
    public EpochNumberInDateTimeFieldsFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
        super(simpleExecutor, processingArguments);
    }

    @Override
    protected BiFunction<String, FuzzingData, Boolean> fieldsFilterFunction() {
        return (field, data) -> {
            Schema<?> schema = data.getRequestPropertyTypes().get(field);
            return CatsModelUtils.isDateTimeSchema(schema);
        };
    }

    @Override
    protected List<Object> getFuzzedValues(Object currentValue) {
        return List.of(Instant.now().minusSeconds(10000).getEpochSecond(),
                Instant.now().plusSeconds(10000).getEpochSecond());
    }

    @Override
    protected String getOriginalType() {
        return "date-time";
    }

    @Override
    protected String getFuzzedType() {
        return "epoch number";
    }
}
