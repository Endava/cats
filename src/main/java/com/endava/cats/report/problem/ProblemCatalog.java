package com.endava.cats.report.problem;

import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.Problem;
import com.endava.cats.model.ProblemKind;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Groups the failing and warning tests of a run into ranked problems, once, when the report is written. Every
 * reportable test gets the id of its problem, so per-test links and counts always match the problem cards.
 *
 * <p>Rank: errors before warnings; then crashes, specific accepted-input findings, rejections with a server message,
 * response violations, status codes, generic accepted input and missing responses; then by operations, by tests and
 * by id.</p>
 */
public final class ProblemCatalog {
    private ProblemCatalog() {
    }

    /**
     * Builds the ranked problems of a run and assigns {@code problemId} to every reportable test.
     *
     * @param summaries the test case summaries of the run; non-reportable and {@code null} entries are ignored
     * @param schemas   the declared response schemas of the contract
     * @return the ranked problems
     */
    public static List<Problem> build(List<CatsTestCaseSummary> summaries, ResponseSchemaNames schemas) {
        Map<ProblemKey, List<CatsTestCaseSummary>> groups = Optional.ofNullable(summaries).orElse(List.of()).stream()
                .filter(ProblemCatalog::isReportable)
                .sorted()
                .collect(Collectors.groupingBy(test -> ProblemKey.of(test, schemas), LinkedHashMap::new, Collectors.toList()));
        groups.forEach((key, tests) -> tests.forEach(test -> test.assignProblem(key.id())));
        CrossLinks links = CrossLinks.of(groups);
        return groups.entrySet().stream()
                .map(entry -> ProblemAssembler.assemble(entry.getKey(), entry.getValue()).toBuilder().alsoIn(links.of(entry.getKey())).build())
                .sorted(Comparator.comparing(ProblemCatalog::rank))
                .toList();
    }

    /**
     * Whether a test is part of a problem: every error and warning is.
     *
     * @param testCase the test case summary, may be {@code null}
     * @return {@code true} for errors and warnings
     */
    public static boolean isReportable(CatsTestCaseSummary testCase) {
        return testCase != null && (testCase.getError() || testCase.getWarning());
    }

    private static Rank rank(Problem problem) {
        return new Rank(problem.isError() ? 0 : 1, kindRank(problem), -problem.occurrences().size(), -problem.testCount(), problem.id());
    }

    private static int kindRank(Problem problem) {
        return switch (problem.kind()) {
            case SERVER_ERROR -> 0;
            case ACCEPTED_REQUEST -> problem.discriminator().startsWith(ResultReasons.UNEXPECTED_STATUS) ? 5 : 1;
            case ERROR_MESSAGE -> 2;
            case RESPONSE_VIOLATION -> 3;
            case STATUS_CODE -> 4;
            case NO_RESPONSE -> 6;
        };
    }

    /**
     * Links problems that share a server message, and accepted-request problems found by the same fuzzer.
     */
    private record CrossLinks(Map<ProblemKey, Set<String>> messages, Map<ProblemKey, Set<String>> fuzzers) {
        static CrossLinks of(Map<ProblemKey, List<CatsTestCaseSummary>> groups) {
            return new CrossLinks(attributes(groups, CatsTestCaseSummary::getErrorTemplateId), attributes(groups, CatsTestCaseSummary::getFuzzer));
        }

        List<String> of(ProblemKey key) {
            return messages.keySet().stream()
                    .filter(other -> !other.equals(key))
                    .filter(other -> shares(messages, key, other) || (bothAccepted(key, other) && shares(fuzzers, key, other)))
                    .map(ProblemKey::id)
                    .distinct()
                    .sorted()
                    .toList();
        }

        private static boolean bothAccepted(ProblemKey key, ProblemKey other) {
            return key.kind() == ProblemKind.ACCEPTED_REQUEST && other.kind() == ProblemKind.ACCEPTED_REQUEST;
        }

        private static boolean shares(Map<ProblemKey, Set<String>> attributes, ProblemKey key, ProblemKey other) {
            return attributes.get(key).stream().anyMatch(attributes.get(other)::contains);
        }

        private static Map<ProblemKey, Set<String>> attributes(Map<ProblemKey, List<CatsTestCaseSummary>> groups,
                                                               Function<CatsTestCaseSummary, String> attribute) {
            return groups.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                    .map(attribute).filter(StringUtils::isNotBlank).collect(Collectors.toSet())));
        }
    }

    private record Rank(int level, int kind, int operations, int tests, String id) implements Comparable<Rank> {
        private static final Comparator<Rank> ORDER = Comparator.comparingInt(Rank::level).thenComparingInt(Rank::kind)
                .thenComparingInt(Rank::operations).thenComparingInt(Rank::tests).thenComparing(Rank::id);

        @Override
        public int compareTo(Rank other) {
            return ORDER.compare(this, other);
        }
    }
}
