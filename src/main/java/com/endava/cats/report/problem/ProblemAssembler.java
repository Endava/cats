package com.endava.cats.report.problem;

import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.Problem;
import com.endava.cats.model.ProblemKind;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds one {@link Problem} from the tests that share a {@link ProblemKey}. The tests are in execution order, so the
 * result does not depend on the order in which tests were reported.
 */
final class ProblemAssembler {
    private static final Comparator<CatsTestCaseSummary> MOST_CONFIDENT = Comparator
            .comparingInt((CatsTestCaseSummary test) -> Optional.ofNullable(test.getConfidence())
                    .map(FindingConfidence::ordinal).orElse(FindingConfidence.values().length));

    private ProblemAssembler() {
    }

    static Problem assemble(ProblemKey key, List<CatsTestCaseSummary> tests) {
        CatsTestCaseSummary first = tests.getFirst();
        boolean identifiedByMessage = key.kind().isIdentifiedByServerMessage();
        String serverMessage = identifiedByMessage ? serverMessage(tests) : null;
        return Problem.builder()
                .id(key.id())
                .kind(key.kind())
                .title(ProblemTitles.of(key, first.getHttpResponseCode(), serverMessage))
                .discriminator(key.discriminator())
                .level(tests.stream().anyMatch(CatsTestCaseSummary::getError) ? CatsResultFactory.ERROR : CatsResultFactory.WARN)
                .reasons(mostFrequentFirst(tests, CatsTestCaseSummary::getResultReason))
                .testCount(tests.size())
                .errorTemplateId(identifiedByMessage ? StringUtils.trimToNull(first.getErrorTemplateId()) : null)
                .errorTemplate(serverMessage)
                .occurrences(occurrences(tests))
                .evidence(evidence(key.kind(), tests))
                .variants(variants(key, tests))
                .alsoIn(List.of())
                .sameAsBaseline((int) tests.stream().filter(test -> StringUtils.isNotBlank(test.getSameErrorAsBaselineTest())).count())
                .build();
    }

    static String operation(CatsTestCaseSummary test) {
        return "%s %s".formatted(StringUtils.defaultString(test.getHttpMethod()).toUpperCase(Locale.ROOT), test.getPath());
    }

    private static String serverMessage(List<CatsTestCaseSummary> tests) {
        return tests.stream().map(CatsTestCaseSummary::getErrorTemplate).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static List<Problem.Occurrence> occurrences(List<CatsTestCaseSummary> tests) {
        return tests.stream()
                .collect(Collectors.groupingBy(ProblemAssembler::operation, Collectors.toList()))
                .entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<CatsTestCaseSummary>>>comparingInt(entry -> entry.getValue().size()).reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> occurrence(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Problem.Occurrence occurrence(String operation, List<CatsTestCaseSummary> tests) {
        List<Problem.FieldCount> fields = counted(tests.stream().flatMap(test -> test.getMutatedFields().stream()).toList()).entrySet().stream()
                .map(entry -> new Problem.FieldCount(entry.getKey(), entry.getValue().intValue()))
                .toList();
        return new Problem.Occurrence(operation, fields,
                tests.stream().map(CatsTestCaseSummary::getId).toList(),
                tests.stream().map(CatsTestCaseSummary::getHttpResponseCode).distinct().sorted().toList(),
                mostFrequentFirst(tests, CatsTestCaseSummary::getFuzzer));
    }

    private static Problem.Evidence evidence(ProblemKind kind, List<CatsTestCaseSummary> tests) {
        if (kind != ProblemKind.ACCEPTED_REQUEST && kind != ProblemKind.RESPONSE_VIOLATION) {
            return null;
        }
        CatsTestCaseSummary mostConfident = tests.stream().min(MOST_CONFIDENT).orElseThrow();
        return new Problem.Evidence(mostConfident.getId(), mostConfident.getConfidence(), mostConfident.getResultDetails());
    }

    private static List<Problem.Variant> variants(ProblemKey key, List<CatsTestCaseSummary> tests) {
        if (key.kind() == ProblemKind.STATUS_CODE) {
            return serverMessageVariants(tests);
        }
        boolean schemaMismatch = key.kind() == ProblemKind.RESPONSE_VIOLATION
                && ResultReasons.is(key.parts().getFirst(), Reason.NOT_MATCHING_RESPONSE_SCHEMA);
        return schemaMismatch ? violationVariants(tests) : List.of();
    }

    private static List<Problem.Variant> serverMessageVariants(List<CatsTestCaseSummary> tests) {
        List<CatsTestCaseSummary> withMessage = tests.stream().filter(test -> StringUtils.isNotBlank(test.getErrorTemplateId())).toList();
        Map<String, String> textById = withMessage.stream()
                .filter(test -> test.getErrorTemplate() != null)
                .collect(Collectors.toMap(CatsTestCaseSummary::getErrorTemplateId, CatsTestCaseSummary::getErrorTemplate, (left, _) -> left));
        return counted(withMessage.stream().map(CatsTestCaseSummary::getErrorTemplateId).toList()).entrySet().stream()
                .map(entry -> new Problem.Variant(entry.getKey(), textById.get(entry.getKey()), entry.getValue().intValue()))
                .toList();
    }

    private static List<Problem.Variant> violationVariants(List<CatsTestCaseSummary> tests) {
        return counted(tests.stream().map(test -> Violations.validatorMessages(test.getResultDetails())).toList()).entrySet().stream()
                .map(entry -> new Problem.Variant(null, entry.getKey(), entry.getValue().intValue()))
                .toList();
    }

    private static List<String> mostFrequentFirst(List<CatsTestCaseSummary> tests, Function<CatsTestCaseSummary, String> attribute) {
        return List.copyOf(counted(tests.stream().map(attribute).map(StringUtils::defaultString).toList()).keySet());
    }

    /**
     * Counts values, most frequent first, then alphabetically.
     */
    private static Map<String, Long> counted(List<String> values) {
        return values.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, _) -> left, LinkedHashMap::new));
    }
}
