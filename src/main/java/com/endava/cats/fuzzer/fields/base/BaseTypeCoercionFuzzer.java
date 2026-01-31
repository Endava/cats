package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Base class for fuzzers that test type coercion issues.
 */
public abstract class BaseTypeCoercionFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());

    private final SimpleExecutor simpleExecutor;
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new BaseTypeCoercionFuzzer instance.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param processingArguments the processing arguments
     */
    public BaseTypeCoercionFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
        this.simpleExecutor = simpleExecutor;
        this.processingArguments = processingArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Skip fuzzer as payload is empty");
            return;
        }

        Set<String> fieldsToBeFuzzed = getFieldsToBeFuzzed(data);

        if (fieldsToBeFuzzed.isEmpty()) {
            logger.skip("No numeric fields found in the request");
            return;
        }

        for (String field : fieldsToBeFuzzed) {
            fuzzField(data, field);
        }
    }

    private Set<String> getFieldsToBeFuzzed(FuzzingData data) {
        return data.getAllFieldsByHttpMethod()
                .stream()
                .filter(field -> JsonUtils.isFieldInJson(data.getPayload(), field))
                .filter(field -> this.fieldsFilterFunction().apply(field, data))
                .collect(Collectors.toSet());
    }

    private void fuzzField(FuzzingData data, String field) {
        Object currentValue = JsonUtils.getVariableFromJson(data.getPayload(), field);

        if (JsonUtils.isNotSet(String.valueOf(currentValue))) {
            logger.debug("Field {} has no value set, skipping", field);
            return;
        }

        for (Object fuzzedValue : getFuzzedValues(currentValue)) {
            String fuzzedPayload = createFuzzedPayload(data.getPayload(), field, fuzzedValue);

            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .expectedResponseCode(processingArguments.isStrictTypes() ?
                                    ResponseCodeFamilyPredefined.FOURXX : ResponseCodeFamilyPredefined.TWOXX)
                            .fuzzingData(data)
                            .logger(logger)
                            .scenario("Send %s fields encoded as %s: field [%s], fuzzed value [%s], original [%s]. This tests type validation and potential type coercion issues."
                                    .formatted(this.getOriginalType(), this.getFuzzedType(), field, fuzzedValue, currentValue))
                            .fuzzer(this)
                            .payload(fuzzedPayload)
                            .build()
            );
        }
    }

    /**
     * Creates a fuzzed payload by replacing the numeric value with a string containing leading zeros.
     * The value is sent as a JSON string (quoted) to ensure the leading zeros are preserved.
     */
    private String createFuzzedPayload(String payload, String field, Object fuzzedValue) {
        return CatsUtil.justReplaceField(payload, field, fuzzedValue).json();
    }


    /**
     * Provides the filter function for the field being fuzzed. This function is used to filter out fields that are not of the expected type.
     *
     * @return the filter function
     */
    protected abstract BiFunction<String, FuzzingData, Boolean> fieldsFilterFunction();

    /**
     * A list of values that will be used for fuzzing.
     *
     * @param currentValue the current value of the field
     * @return the list of values for fuzzing
     */
    protected abstract List<Object> getFuzzedValues(Object currentValue);

    /**
     * The initial data type that is in focus for fuzzing.
     *
     * @return the original data type of the field
     */
    protected abstract String getOriginalType();

    /**
     * The fuzzed data type i.e. with which type the original one is replaced with.
     *
     * @return the fuzzed data type of the field
     */
    protected abstract String getFuzzedType();


    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return String.format("iterate through each %s field and send valid values encoded as %s to check for type coercion issues", this.getOriginalType(), this.getFuzzedType());
    }
}
