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

import java.time.Instant;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Fuzzer that sends date-time field values as epoch numbers to check for type coercion issues.
 */
@Singleton
@FieldFuzzer
public class EpochNumberInDateTimeFieldsFuzzer extends BaseTypeCoercionFuzzer {
    private static final long DEFAULT_BASE_EPOCH_SECONDS = 1_704_067_200L;
    private static final long EPOCH_OFFSET_SECONDS = 10_000L;

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
        long baseEpoch = parseEpochSeconds(currentValue);
        return List.of(baseEpoch - EPOCH_OFFSET_SECONDS, baseEpoch + EPOCH_OFFSET_SECONDS);
    }

    private static long parseEpochSeconds(Object currentValue) {
        if (currentValue != null) {
            try {
                return Instant.parse(String.valueOf(currentValue).trim()).getEpochSecond();
            } catch (RuntimeException _) {
                // fall back to deterministic reference epoch
            }
        }
        return DEFAULT_BASE_EPOCH_SECONDS;
    }

    @Override
    protected String getOriginalType() {
        return "date-time";
    }

    @Override
    protected String getFuzzedType() {
        return "epoch number";
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.TRACE);
    }
}
