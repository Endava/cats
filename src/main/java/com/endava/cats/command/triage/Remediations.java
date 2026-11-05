package com.endava.cats.command.triage;

import com.endava.cats.fuzzer.fields.AlternativeDateTimeFormatsFieldsFuzzer;
import com.endava.cats.fuzzer.fields.BooleanStringInBooleanFieldsFuzzer;
import com.endava.cats.fuzzer.fields.BooleanValuesInStringFieldsFuzzer;
import com.endava.cats.fuzzer.fields.EnumCaseVariantFieldsFuzzer;
import com.endava.cats.fuzzer.fields.EnumNumericOrdinalFieldsFuzzer;
import com.endava.cats.fuzzer.fields.EpochNumberInDateTimeFieldsFuzzer;
import com.endava.cats.fuzzer.fields.HomoglyphEnumFieldsFuzzer;
import com.endava.cats.fuzzer.fields.InvalidValuesInEnumsFieldsFuzzer;
import com.endava.cats.fuzzer.fields.LeadingZerosInNumericFieldsFuzzer;
import com.endava.cats.fuzzer.fields.NumericBooleansInBooleanFieldsFuzzer;
import com.endava.cats.fuzzer.fields.NumericStringsInNumericFieldsFuzzer;
import com.endava.cats.fuzzer.fields.NumericValuesInStringFieldsFuzzer;
import com.endava.cats.fuzzer.fields.ScientificNotationStringsInDecimalFieldsFuzzer;
import com.endava.cats.fuzzer.fields.SingleElementArrayToScalarFieldsFuzzer;
import com.endava.cats.fuzzer.fields.SmallNegativeAndInt32BoundaryFieldsFuzzer;
import com.endava.cats.fuzzer.fields.SpecialFloatTokensInNumericFieldsFuzzer;
import com.endava.cats.fuzzer.fields.WhitespacePaddedNumericFieldsFuzzer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.List;

/**
 * Actionable remediation texts, distinguishing failures only {@code --refData} can fix (server rejects a value the
 * fuzzer is mutating), failures {@code --correlations} can fix (cross-field requirements), and contract or CLI-flag fixes.
 */
final class Remediations {
    private static final List<Class<?>> TYPE_COERCION_FUZZERS = List.of(
            AlternativeDateTimeFormatsFieldsFuzzer.class, BooleanStringInBooleanFieldsFuzzer.class,
            BooleanValuesInStringFieldsFuzzer.class, EpochNumberInDateTimeFieldsFuzzer.class,
            LeadingZerosInNumericFieldsFuzzer.class, NumericBooleansInBooleanFieldsFuzzer.class,
            NumericStringsInNumericFieldsFuzzer.class, NumericValuesInStringFieldsFuzzer.class,
            ScientificNotationStringsInDecimalFieldsFuzzer.class, SingleElementArrayToScalarFieldsFuzzer.class,
            SmallNegativeAndInt32BoundaryFieldsFuzzer.class, SpecialFloatTokensInNumericFieldsFuzzer.class,
            WhitespacePaddedNumericFieldsFuzzer.class);
    private static final List<Class<?>> STRICT_ENUM_FUZZERS = List.of(
            HomoglyphEnumFieldsFuzzer.class, EnumNumericOrdinalFieldsFuzzer.class);

    private Remediations() {
    }

    static ClusterClassification.Explanation fieldDomain(TriageTestCase testCase, String field, ErrorMessage message) {
        String hashField = field.replace('.', '#');
        return new ClusterClassification.Explanation(
                "Server rejected the value that %s sent in fuzzed field [%s] (%s)".formatted(testCase.fuzzer(), field, message.signature()),
                ("Pin \"%s\" to a known-valid value in --refData under the path-only key \"%s\" (use # for nesting) so %s and other "
                        + "boundary fuzzers skip it; --correlations cannot override a field the fuzzer is mutating. If the contract allows "
                        + "values the server rejects, tighten the enum/pattern/format/length constraints of %s.")
                        .formatted(hashField, testCase.path(), testCase.fuzzer(), field));
    }

    static ClusterClassification.Explanation crossField(TriageTestCase testCase, ErrorMessage message, ClassificationContext context) {
        String diagnosis = "Domain / cross-field validation rejected request (%s)".formatted(message.signature());
        return context.ruleFor(testCase)
                .map(rule -> new ClusterClassification.Explanation(diagnosis,
                        ("Review the synthesized --correlations rule for %s (`--export-correlations correlations.yml`): %s %s; "
                                + "replace the placeholder and confirm the trigger exists in the generated payload.")
                                .formatted(testCase.path(), describeWhen(rule), describeAction(rule))))
                .orElseGet(() -> new ClusterClassification.Explanation(diagnosis,
                        ("Supply valid values for %s: fixed or existing values (IDs, checksums, closed vocabularies) via --refData, "
                                + "values depending on other fields via a --correlations rule.").formatted(testCase.path())));
    }

    static String validationBypass(TriageTestCase testCase) {
        String operation = testCase.operation();
        if (testCase.isFuzzer(InvalidValuesInEnumsFieldsFuzzer.class)) {
            return "Validate enum values in %s and reject invalid values with 400/422 (or pass --allowInvalidEnumValues if intentional).".formatted(operation);
        }
        if (testCase.isFuzzer(EnumCaseVariantFieldsFuzzer.class)) {
            return "Reject case-variant enum values in %s, or drop --no-allowDifferentCaseEnumValues if case-insensitive enums are intended.".formatted(operation);
        }
        if (STRICT_ENUM_FUZZERS.stream().anyMatch(testCase::isFuzzer)) {
            return "Validate enum values in %s and reject look-alike or ordinal values with 400/422.".formatted(operation);
        }
        if (TYPE_COERCION_FUZZERS.stream().anyMatch(testCase::isFuzzer)) {
            return "Reject coerced types in %s with 4XX, or pass --no-strictTypes if lenient type coercion is intended.".formatted(operation);
        }
        if (Strings.CI.contains(testCase.testCase().getScenario(), "is required [TRUE")) {
            return ("The field is required by the contract or a --correlations rule, but %s accepts requests without it: "
                    + "remove it from `required` in the contract, or tighten the correlation rule's paths/when guard.").formatted(operation);
        }
        return "Enforce input validation in %s so invalid input from %s is rejected with 4XX.".formatted(operation, testCase.fuzzer());
    }

    static String contractMismatch(TriageTestCase testCase, boolean undocumented) {
        if (undocumented) {
            return "If HTTP %d is intended, add it to %s via `cats triage --export-contract-patch contract-patch.yml`."
                    .formatted(testCase.statusCode(), testCase.operation());
        }
        if (testCase.expected().expectsOnly2xx() && testCase.statusCode() >= 400) {
            return ("Fuzzer [%s] sent a schema-valid request that %s rejected with HTTP %d: supply valid domain values via --refData "
                    + "or --correlations, or tighten the contract constraints so the request is no longer considered valid.")
                    .formatted(testCase.fuzzer(), testCase.operation(), testCase.statusCode());
        }
        return "Fuzzer [%s] (%s) received HTTP %d on %s; align the service status code or the contract."
                .formatted(testCase.fuzzer(), StringUtils.defaultIfBlank(testCase.testCase().getExpectedResult(), "expected different status"),
                        testCase.statusCode(), testCase.operation());
    }

    private static String describeWhen(SynthesizedCorrelationRule rule) {
        return rule.when() == null ? "always" : "when " + rule.when();
    }

    private static String describeAction(SynthesizedCorrelationRule rule) {
        return FailureIntent.REMOVE.name().equals(rule.action())
                ? "omit " + rule.targetField()
                : "set " + rule.targetField();
    }
}
