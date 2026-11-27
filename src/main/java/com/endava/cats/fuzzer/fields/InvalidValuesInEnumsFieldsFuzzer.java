package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.fields.base.BaseBoundaryFieldFuzzer;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fuzzer that sends invalid values in enums. There is an argument {@code --allowInvalidEnumValues}
 * which will influence the expected response codes by this fuzzer.
 */
@Singleton
@FieldFuzzer
public class InvalidValuesInEnumsFieldsFuzzer extends BaseBoundaryFieldFuzzer {
    private static final int MAX_GENERATION_ATTEMPTS = 50;
    private static final String FALLBACK_INVALID_ENUM = "CATS_INVALID_ENUM";
    final ProcessingArguments processingArguments;

    /**
     * Creates a new InvalidValuesInEnumsFieldsFuzzer instance.
     *
     * @param sc                  the service caller
     * @param lr                  the test case listener
     * @param cp                  files arguments
     * @param processingArguments to get the {@code --allowInvalidEnumValues} argument value
     */
    public InvalidValuesInEnumsFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp, ProcessingArguments processingArguments) {
        super(sc, lr, cp);
        this.processingArguments = processingArguments;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "invalid ENUM values";
    }

    @Override
    public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
        return List.of("string");
    }

    @Override
    public String getBoundaryValue(Schema schema) {
        if (schema == null || CatsUtil.isEmpty(schema.getEnum())) {
            return null;
        }
        List<String> enumValues = ((List<?>) schema.getEnum()).stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
        if (enumValues.isEmpty()) {
            return null;
        }
        Set<String> normalized = enumValues.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        int length = Math.max(1, enumValues.getFirst().length());
        return generateNonMember(length, normalized);
    }

    private static String generateNonMember(int length, Set<String> normalizedEnumValues) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = StringGenerator.generate("[A-Z]+", length, length);
            if (!normalizedEnumValues.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        if (length == 1) {
            return findUnusedSingleChar(normalizedEnumValues)
                    .orElseGet(() -> buildUniqueFallback(normalizedEnumValues));
        }
        return buildUniqueFallback(normalizedEnumValues);
    }

    private static Optional<String> findUnusedSingleChar(Set<String> normalizedEnumValues) {
        for (char c = 'A'; c <= 'Z'; c++) {
            if (!normalizedEnumValues.contains(String.valueOf(Character.toLowerCase(c)))) {
                return Optional.of(String.valueOf(c));
            }
        }
        for (char c = '0'; c <= '9'; c++) {
            if (!normalizedEnumValues.contains(String.valueOf(c))) {
                return Optional.of(String.valueOf(c));
            }
        }
        return Optional.empty();
    }

    private static String buildUniqueFallback(Set<String> normalizedEnumValues) {
        String candidate = FALLBACK_INVALID_ENUM;
        int suffix = 1;
        while (normalizedEnumValues.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = FALLBACK_INVALID_ENUM + "_" + suffix++;
        }
        return candidate;
    }

    @Override
    public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
        Schema<?> schema = data.getRequestPropertyTypes().get(fuzzedField);
        return schema != null && CatsUtil.isNotEmpty(schema.getEnum())
                && schema.getEnum().stream().anyMatch(Objects::nonNull);
    }

    @Override
    public String description() {
        return "iterate through each ENUM field and send invalid values";
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenRequiredFieldsAreFuzzed() {
        return processingArguments.isAllowInvalidEnumValues() ? ResponseCodeFamilyPredefined.TWOXX : ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenOptionalFieldsAreFuzzed() {
        return processingArguments.isAllowInvalidEnumValues() ? ResponseCodeFamilyPredefined.TWOXX : ResponseCodeFamilyPredefined.FOURXX;
    }

    @Override
    public ResponseCodeFamily getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern() {
        return processingArguments.isAllowInvalidEnumValues() ? ResponseCodeFamilyPredefined.TWOXX : ResponseCodeFamilyPredefined.FOURXX;
    }
}
