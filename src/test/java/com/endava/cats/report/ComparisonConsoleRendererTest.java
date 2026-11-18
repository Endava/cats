package com.endava.cats.report;

import com.endava.cats.command.model.ComparisonResult;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.ProblemKind;
import com.endava.cats.report.problem.OccurrenceDelta;
import com.endava.cats.report.problem.OccurrenceDelta.MatchedBy;
import com.endava.cats.report.problem.OccurrenceDelta.State;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.stream.IntStream;

import static com.endava.cats.report.ComparisonFixtures.problem;
import static com.endava.cats.report.ComparisonFixtures.report;

@QuarkusTest
class ComparisonConsoleRendererTest {
    @Test
    void shouldShowAllUnchangedOccurrencesWithoutPrintingUnchangedDetailsOrEmptySections() {
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);
        var unchanged = IntStream.range(0, 45).mapToObj(_ -> delta(State.UNCHANGED, 1, 1)).toList();

        BaselineComparisonEngine.renderToConsole(logger, result(List.of(), List.of(), unchanged, List.of()));

        Mockito.verify(logger).noFormat("Baseline: {}", "baseline");
        Mockito.verify(logger).noFormat("Target:   {}", "target");
        Mockito.verify(logger, Mockito.times(2)).noFormat("");
        Mockito.verify(logger).noFormat("Occurrences: {} new | {} fixed | {} unchanged | {} not comparable", 0, 0, 45, 0);
        Mockito.verify(logger).noFormat("No new regressions detected.");
        Mockito.verifyNoMoreInteractions(logger);
    }

    @Test
    void shouldDistinguishTheChangedCountsFromTheTotalUnchangedOccurrences() {
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);
        var unchanged = List.of(delta(State.UNCHANGED, 1, 1), delta(State.UNCHANGED, 2, 4));

        ComparisonConsoleRenderer.render(logger, result(List.of(), List.of(), unchanged, List.of()), null);

        Mockito.verify(logger).noFormat("{} ({})", "Unchanged (test counts changed; 2 total unchanged)", 1);
        Mockito.verify(logger).noFormat("    Baseline: {} | Target: {}", "2 tests", "4 tests");
    }

    @Test
    void shouldExplainMissingSavedCoverageAndKeepRemainingTargetFindingsVisible() {
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);
        var baseline = report(problem("p", ProblemKind.STATUS_CODE, 4));
        var target = CatsTestReport.builder().problems(List.of()).errors(4).warnings(346).skippedFromReporting(19_647).build();
        var reports = new BaselineComparisonEngine.Reports("baseline", "target", baseline, target);

        ComparisonConsoleRenderer.render(logger, result(List.of(), List.of(), List.of(), List.of(delta(State.NOT_COMPARABLE, 4, 0))), reports);

        Mockito.verify(logger).noFormat("    Baseline: {} | Target: {}", "4 tests", "not observed");
        Mockito.verify(logger).noFormat("    Coverage: no saved {} results for {}.", "target", "RemoveFields");
        Mockito.verify(logger).noFormat("{} could not be assessed.", "1 occurrence");
        Mockito.verify(logger).noFormat("Reporting filters omitted results: baseline {}, target {}. Coverage uses saved tests.", 0L, 19_647L);
        Mockito.verify(logger).noFormat("Target findings: {} | {}.", "4 errors", "346 warnings");
        Mockito.verify(logger).noFormat("No new regressions detected.");
    }

    @Test
    void shouldExplainCoverageWithoutReportMetadataForTheRuntimeBaselineGate() {
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);
        var incomparable = List.of(delta(State.NOT_COMPARABLE, 1, 0), delta(State.NOT_COMPARABLE, 0, 2));

        BaselineComparisonEngine.renderToConsole(logger, result(List.of(), List.of(), List.of(), incomparable));

        Mockito.verify(logger).noFormat("    Coverage: no saved {} results for {}.", "target", "this occurrence's fuzzers");
        Mockito.verify(logger).noFormat("    Coverage: no saved {} results for {}.", "baseline", "this occurrence's fuzzers");
        Mockito.verify(logger).noFormat("{} could not be assessed.", "2 occurrences");
    }

    @Test
    void shouldExplainWhyNewWarningsDoNotFailTheSelectedGate() {
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);
        var warning = new OccurrenceDelta(State.NEW, "p", ProblemKind.STATUS_CODE, "warn", "Warning", "POST /orders", "",
                List.of(400), 0, 1, null);

        ComparisonConsoleRenderer.render(logger, result(List.of(warning), List.of(), List.of(), List.of()), null);

        Mockito.verify(logger).noFormat("  {} [{}] {}{}", "p", "WARN", "POST /orders", "");
        Mockito.verify(logger).noFormat("No new regressions detected.");
        Mockito.verify(logger).noFormat("New warnings do not fail this comparison unless --failOnNewWarnings is enabled.");
    }

    @Test
    void shouldNotSuggestCoverageLossWhenFilteringDidNotHideAnOccurrence() {
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);
        var target = CatsTestReport.builder().problems(List.of()).errors(1).warnings(0).skippedFromReporting(100).build();
        var reports = new BaselineComparisonEngine.Reports("baseline", "target", report(), target);

        ComparisonConsoleRenderer.render(logger, result(List.of(), List.of(), List.of(), List.of()), reports);

        Mockito.verify(logger).noFormat("Target findings: {} | {}.", "1 error", "0 warnings");
        Mockito.verify(logger, Mockito.never()).noFormat(Mockito.startsWith("Reporting filters omitted"), Mockito.any(), Mockito.any());
    }

    private static ComparisonResult result(List<OccurrenceDelta> added, List<OccurrenceDelta> fixed,
                                           List<OccurrenceDelta> unchanged, List<OccurrenceDelta> incomparable) {
        return new ComparisonResult("baseline", "target", 1, added.stream().anyMatch(OccurrenceDelta::isError),
                added, fixed, unchanged, incomparable, 0);
    }

    private static OccurrenceDelta delta(State state, int baseline, int target) {
        return new OccurrenceDelta(state, "p", ProblemKind.STATUS_CODE, "error", "Synthetic problem", "POST /orders", "",
                List.of(400), baseline, target, state == State.UNCHANGED ? MatchedBy.ID : null);
    }
}
