package com.endava.cats.report;

import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.Problem;
import com.endava.cats.report.problem.ProblemCatalog;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * The problems view of the summary report: ranked problem cards, the pre-rendered pivots (reason, operation, status),
 * the problem of every row of the tests table and the run-level hint. Every number and link is derived from the
 * persisted problems only, so the cards, the table and the test-page banners cannot disagree.
 *
 * @param hasProblems  whether the run has failing or warning tests
 * @param problemCount the number of problems
 * @param headline     e.g. {@code 21 distinct problems in 386 failing tests (80 errors, 306 warnings) on 26 operations}
 * @param hint         the run-level hint, or {@code null}
 * @param cards        the ranked problem cards
 * @param byReason     the problems per result reason
 * @param byOperation  the problems per operation
 * @param byStatus     the problems per HTTP status code
 * @param testRows     every test of the report with its problem
 * @param javascript   whether the report uses JavaScript
 */
public record ProblemPage(boolean hasProblems, int problemCount, String headline, String hint, List<ProblemCard> cards,
                          List<PivotGroup> byReason, List<PivotGroup> byOperation, List<PivotGroup> byStatus,
                          List<TestRow> testRows, boolean javascript) {
    private static final double CONFIGURATION_HINT_SHARE = 0.9;
    private static final int CONFIGURATION_HINT_MIN_TESTS = 50;
    private static final int TITLE_LABEL_LENGTH = 48;

    /**
     * Builds the problems view.
     *
     * @param problems   the ranked problems of the report
     * @param testCases  every test of the report
     * @param javascript whether the report uses JavaScript
     * @return the problems view
     */
    public static ProblemPage of(List<Problem> problems, List<CatsTestCaseSummary> testCases, boolean javascript) {
        List<Problem> ranked = Optional.ofNullable(problems).orElse(List.of());
        List<CatsTestCaseSummary> tests = Optional.ofNullable(testCases).orElse(List.of()).stream().filter(Objects::nonNull).toList();
        Map<String, CatsTestCaseSummary> testsById = tests.stream()
                .collect(Collectors.toMap(CatsTestCaseSummary::getId, Function.identity(), (left, _) -> left));
        List<ProblemCard> cards = ProblemCards.of(ranked, testsById, javascript);
        Map<String, ProblemCard> cardsById = cards.stream().collect(Collectors.toMap(ProblemCard::id, Function.identity(), (left, _) -> left));
        return new ProblemPage(!ranked.isEmpty(), ranked.size(), headline(ranked, testsById), hint(ranked), cards,
                ProblemPivots.byReason(cards, ranked, testsById), ProblemPivots.byOperation(cards, ranked), ProblemPivots.byStatus(cards, ranked, testsById),
                tests.stream().map(test -> TestRow.of(test, cardsById.get(test.getProblemId()))).toList(), javascript);
    }

    /**
     * Whether the run-level hint is shown.
     *
     * @return {@code true} when one problem holds most tests
     */
    public boolean hasHint() {
        return hint != null;
    }

    /**
     * The problems the tests table can be filtered by.
     *
     * @return one option per problem card
     */
    public List<ProblemCard> filterOptions() {
        return cards;
    }

    private static String headline(List<Problem> problems, Map<String, CatsTestCaseSummary> testsById) {
        List<CatsTestCaseSummary> failing = problems.stream().flatMap(problem -> problem.occurrences().stream())
                .flatMap(occurrence -> occurrence.testIds().stream()).map(testsById::get).filter(Objects::nonNull).toList();
        long errors = failing.stream().filter(CatsTestCaseSummary::getError).count();
        long operations = problems.stream().flatMap(problem -> problem.occurrences().stream()).map(Problem.Occurrence::operation).distinct().count();
        return "%d distinct %s in %d failing %s (%d %s, %d %s) on %d %s".formatted(problems.size(), plural(problems.size(), "problem"),
                failing.size(), plural(failing.size(), "test"), errors, plural(errors, "error"), failing.size() - errors,
                plural(failing.size() - errors, "warning"), operations, plural(operations, "operation"));
    }

    private static String hint(List<Problem> problems) {
        int tests = problems.stream().mapToInt(Problem::testCount).sum();
        return problems.stream().max(Comparator.comparingInt(Problem::testCount))
                .filter(top -> tests >= CONFIGURATION_HINT_MIN_TESTS && top.testCount() >= CONFIGURATION_HINT_SHARE * tests)
                .map(top -> ("Most tests failed the same way (%d of %d). This usually means a configuration problem (wrong --server or base path, "
                        + "authentication, seed data), not %d bugs. Run cats triage for per-operation hints.").formatted(top.testCount(), tests, top.testCount()))
                .orElse(null);
    }

    static String plural(long count, String noun) {
        return count == 1 ? noun : noun + "s";
    }

    static String label(ProblemCard card) {
        return "%d. %s".formatted(card.rank(), StringUtils.abbreviate(card.navigationTitle(), TITLE_LABEL_LENGTH));
    }

    /**
     * A row of the tests table.
     *
     * @param tc            the test
     * @param problemAnchor the anchor of its problem card, or {@code null} for passing tests
     * @param problemLabel  the rank and short title of its problem
     * @param operation     the HTTP method and path
     * @param pathHtml      the escaped path with line-break hints and highlighted path parameters, rendered once on the
     *                      server so that opening large reports does not rewrite every row in the browser
     */
    public record TestRow(CatsTestCaseSummary tc, String problemAnchor, String problemLabel, String operation, String pathHtml) {
        private static final Pattern PATH_PARAMETER = Pattern.compile("(\\{[^}]+})");

        static TestRow of(CatsTestCaseSummary test, ProblemCard card) {
            String operation = "%s %s".formatted(StringUtils.upperCase(test.getHttpMethod()), test.getPath());
            String pathHtml = pathHtml(test.getPath());
            return card == null || !ProblemCatalog.isReportable(test)
                    ? new TestRow(test, null, null, operation, pathHtml)
                    : new TestRow(test, card.anchor(), label(card), operation, pathHtml);
        }

        static String pathHtml(String path) {
            String escaped = StringUtils.defaultString(path).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
            return PATH_PARAMETER.matcher(escaped.replace("/", "/<wbr>")).replaceAll("<span class=\"param-tok\">$1</span>");
        }
    }

    /**
     * A group of a pivot view, e.g. one operation with its problems.
     *
     * @param label   the reason, operation or status
     * @param tests   the number of tests in the group
     * @param entries the problems of the group
     */
    public record PivotGroup(String label, int tests, List<PivotEntry> entries) {
    }

    /**
     * A problem within a pivot group.
     *
     * @param rank   the rank of the problem
     * @param anchor the anchor of its card
     * @param title  the title of the problem
     * @param level  {@code error} or {@code warn}
     * @param tests  the number of tests of the problem in the group
     */
    public record PivotEntry(int rank, String anchor, String title, String level, int tests) {
    }

    /**
     * Groups problem cards into pivot views.
     */
    static final class ProblemPivots {
        private ProblemPivots() {
        }

        static List<PivotGroup> byReason(List<ProblemCard> cards, List<Problem> problems, Map<String, CatsTestCaseSummary> testsById) {
            return grouped(cards, problems, problem -> countedTests(problem, testsById, CatsTestCaseSummary::getResultReason));
        }

        static List<PivotGroup> byStatus(List<ProblemCard> cards, List<Problem> problems, Map<String, CatsTestCaseSummary> testsById) {
            return grouped(cards, problems, problem -> countedTests(problem, testsById, test -> "HTTP " + test.getHttpResponseCode()));
        }

        static List<PivotGroup> byOperation(List<ProblemCard> cards, List<Problem> problems) {
            return grouped(cards, problems, problem -> problem.occurrences().stream()
                    .collect(Collectors.toMap(Problem.Occurrence::operation, occurrence -> occurrence.testIds().size(), Integer::sum, LinkedHashMap::new)));
        }

        private static Map<String, Integer> countedTests(Problem problem, Map<String, CatsTestCaseSummary> testsById,
                                                         Function<CatsTestCaseSummary, String> attribute) {
            return problem.occurrences().stream().flatMap(occurrence -> occurrence.testIds().stream())
                    .map(testsById::get).filter(Objects::nonNull)
                    .collect(Collectors.toMap(test -> StringUtils.defaultString(attribute.apply(test)), _ -> 1, Integer::sum, LinkedHashMap::new));
        }

        private static List<PivotGroup> grouped(List<ProblemCard> cards, List<Problem> problems, Function<Problem, Map<String, Integer>> testsPerGroup) {
            Map<String, List<PivotEntry>> groups = new LinkedHashMap<>();
            IntStream.range(0, problems.size()).forEach(index -> testsPerGroup.apply(problems.get(index)).forEach((group, tests) ->
                    groups.computeIfAbsent(group, _ -> new ArrayList<>()).add(entry(cards.get(index), tests))));
            return groups.entrySet().stream()
                    .map(group -> new PivotGroup(group.getKey(), group.getValue().stream().mapToInt(PivotEntry::tests).sum(), List.copyOf(group.getValue())))
                    .sorted(Comparator.comparingInt(PivotGroup::tests).reversed().thenComparing(PivotGroup::label))
                    .toList();
        }

        private static PivotEntry entry(ProblemCard card, int tests) {
            return new PivotEntry(card.rank(), card.anchor(), card.filterTitle(), card.level(), tests);
        }
    }
}
