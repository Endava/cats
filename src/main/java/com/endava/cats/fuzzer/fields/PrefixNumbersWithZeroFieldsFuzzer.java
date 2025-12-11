package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fuzzer that sends numeric field values as strings with leading zeros.
 * <p>
 * This tests how APIs handle type coercion and validation when receiving
 * string representations of numbers with leading zeros (e.g., "00123" instead of 123).
 * </p>
 * <p>
 * Leading zeros in numeric strings can cause issues:
 * <ul>
 *   <li>Some parsers interpret leading zeros as octal numbers (0123 = 83 in decimal)</li>
 *   <li>Type coercion may silently accept invalid input</li>
 *   <li>String comparison vs numeric comparison issues</li>
 *   <li>Database storage inconsistencies</li>
 * </ul>
 * </p>
 */
@Singleton
@FieldFuzzer
public class PrefixNumbersWithZeroFieldsFuzzer implements Fuzzer {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(PrefixNumbersWithZeroFieldsFuzzer.class);
    private static final List<String> ZERO_PREFIXES = List.of("0", "00", "000");

    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new PrefixNumbersWithZeroFieldsFuzzer instance.
     *
     * @param simpleExecutor the executor used to run the fuzz logic
     */
    public PrefixNumbersWithZeroFieldsFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            LOGGER.skip("Skip fuzzer as payload is empty");
            return;
        }

        Set<String> numericFields = getNumericFields(data);

        if (numericFields.isEmpty()) {
            LOGGER.skip("No numeric fields found in the request");
            return;
        }

        for (String field : numericFields) {
            fuzzField(data, field);
        }
    }

    private Set<String> getNumericFields(FuzzingData data) {
        return data.getAllFieldsByHttpMethod()
                .stream()
                .filter(field -> JsonUtils.isFieldInJson(data.getPayload(), field))
                .filter(field -> {
                    Schema<?> schema = data.getRequestPropertyTypes().get(field);
                    return CatsModelUtils.isNumberSchema(schema) || CatsModelUtils.isIntegerSchema(schema);
                })
                .collect(Collectors.toSet());
    }

    private void fuzzField(FuzzingData data, String field) {
        Object currentValue = JsonUtils.getVariableFromJson(data.getPayload(), field);

        if (JsonUtils.isNotSet(String.valueOf(currentValue))) {
            LOGGER.debug("Field {} has no value set, skipping", field);
            return;
        }

        for (String prefix : ZERO_PREFIXES) {
            String fuzzedValue = prefix + currentValue;
            String fuzzedPayload = createFuzzedPayload(data.getPayload(), field, fuzzedValue);

            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                            .fuzzingData(data)
                            .logger(LOGGER)
                            .scenario("Send numeric field [%s] as string with leading zeros: [%s] (original: [%s]). This tests type validation and potential octal interpretation issues."
                                    .formatted(field, fuzzedValue, currentValue))
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
    private String createFuzzedPayload(String payload, String field, String fuzzedValue) {
        return CatsUtil.justReplaceField(payload, field, fuzzedValue).json();
    }

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
        return "iterate through each numeric field and send values as strings with leading zeros to test type validation";
    }
}