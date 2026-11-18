package com.endava.cats.report;

import com.endava.cats.command.model.ComparisonResult;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.report.problem.OccurrenceDelta;
import com.endava.cats.report.problem.OccurrenceDelta.State;
import io.github.ludovicianul.prettylogger.PrettyLogger;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Presents occurrence changes without confusing missing coverage with a verified fix or a clean target run. */
final class ComparisonConsoleRenderer {
    private ComparisonConsoleRenderer() {
    }

    static void render(PrettyLogger logger, ComparisonResult result, BaselineComparisonEngine.Reports reports) {
        new Context(logger, result, reports).render();
    }

    private record Context(PrettyLogger logger, ComparisonResult result, BaselineComparisonEngine.Reports reports) {
        void render() {
            logger.noFormat("Baseline: {}", result.baseline());
            logger.noFormat("Target:   {}", result.target());
            logger.noFormat("");
            logger.noFormat("Occurrences: {} new | {} fixed | {} unchanged | {} not comparable",
                    result.newOccurrences().size(), result.fixedOccurrences().size(), result.unchangedOccurrences().size(), result.notComparable().size());
            renderSection("New", result.newOccurrences());
            renderSection("Fixed", result.fixedOccurrences());
            renderSection("Not comparable", result.notComparable());
            renderCountChanges();
            renderVerdict();
        }

        private void renderSection(String name, List<OccurrenceDelta> deltas) {
            if (deltas.isEmpty()) {
                return;
            }
            logger.noFormat("");
            logger.noFormat("{} ({})", name, deltas.size());
            deltas.forEach(this::renderOccurrence);
        }

        private void renderCountChanges() {
            List<OccurrenceDelta> changed = result.unchangedOccurrences().stream()
                    .filter(delta -> delta.baselineTests() != delta.targetTests()).toList();
            if (!changed.isEmpty()) {
                renderSection("Unchanged (test counts changed; %d total unchanged)".formatted(result.unchangedOccurrences().size()), changed);
            }
        }

        private void renderOccurrence(OccurrenceDelta delta) {
            String level = Optional.ofNullable(delta.level()).orElse("unknown").toUpperCase(Locale.ROOT);
            String field = delta.field().isEmpty() ? "" : " [%s]".formatted(delta.field());
            logger.noFormat("  {} [{}] {}{}", delta.problemId(), level, delta.operation(), field);
            logger.noFormat("    {}", delta.title());
            logger.noFormat("    Baseline: {} | Target: {}", testCount(delta, true), testCount(delta, false));
            if (delta.state() == State.NOT_COMPARABLE) {
                String side = delta.baselineTests() == 0 ? "baseline" : "target";
                List<String> fuzzers = fuzzers(delta);
                logger.noFormat("    Coverage: no saved {} results for {}.", side,
                        fuzzers.isEmpty() ? "this occurrence's fuzzers" : String.join(", ", fuzzers));
            }
        }

        private List<String> fuzzers(OccurrenceDelta delta) {
            if (reports == null) {
                return List.of();
            }
            CatsTestReport source = delta.baselineTests() == 0 ? reports.target() : reports.baseline();
            return source.getProblems().stream().filter(problem -> problem.id().equals(delta.problemId()))
                    .flatMap(problem -> problem.occurrences().stream()).filter(occurrence -> occurrence.operation().equals(delta.operation()))
                    .flatMap(occurrence -> occurrence.fuzzers().stream()).distinct().toList();
        }

        private void renderVerdict() {
            logger.noFormat("");
            logger.noFormat(result.regression() ? "New regressions detected." : "No new regressions detected.");
            if (!result.regression() && !result.newOccurrences().isEmpty()) {
                logger.noFormat("New warnings do not fail this comparison unless --failOnNewWarnings is enabled.");
            }
            if (!result.notComparable().isEmpty()) {
                logger.noFormat("{} could not be assessed.", count(result.notComparable().size(), "occurrence"));
                renderFilteringNote();
            }
            if (reports != null) {
                logger.noFormat("Target findings: {} | {}.", count(reports.target().getErrors(), "error"), count(reports.target().getWarnings(), "warning"));
            }
        }

        private void renderFilteringNote() {
            if (reports != null && (reports.baseline().getSkippedFromReporting() > 0 || reports.target().getSkippedFromReporting() > 0)) {
                logger.noFormat("Reporting filters omitted results: baseline {}, target {}. Coverage uses saved tests.",
                        reports.baseline().getSkippedFromReporting(), reports.target().getSkippedFromReporting());
            }
        }

        private static String testCount(OccurrenceDelta delta, boolean baseline) {
            int tests = baseline ? delta.baselineTests() : delta.targetTests();
            return delta.state() == State.NOT_COMPARABLE && tests == 0 ? "not observed" : count(tests, "test");
        }

        private static String count(long value, String noun) {
            return "%d %s%s".formatted(value, noun, value == 1 ? "" : "s");
        }
    }
}
