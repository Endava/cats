package com.endava.cats.report;

import com.endava.cats.command.model.ComparisonResult;
import com.endava.cats.exception.UnsupportedReportException;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.Problem;
import com.endava.cats.model.ProblemKind;
import com.endava.cats.report.problem.OccurrenceDelta;
import com.endava.cats.report.problem.OccurrenceDelta.MatchedBy;
import com.endava.cats.report.problem.OccurrenceDelta.State;
import com.endava.cats.report.problem.OccurrenceKey;
import com.endava.cats.report.problem.ServerMessagePattern;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Compares persisted problem occurrences. Test-count changes never create regressions. */
public final class BaselineComparisonEngine {
    public static final String SUMMARY_FILE_NAME = "cats-summary-report.json";

    private BaselineComparisonEngine() {
    }

    public static CatsTestReport loadReport(Path reportPath) throws IOException {
        Path resolved = resolveSummaryFile(reportPath);
        try (Reader reader = Files.newBufferedReader(resolved, StandardCharsets.UTF_8)) {
            CatsTestReport report = JsonUtils.GSON.fromJson(reader, CatsTestReport.class);
            if (report == null) {
                throw new IOException("Empty or invalid CATS summary report: " + resolved);
            }
            requireProblems(report, resolved.toString());
            return report;
        } catch (com.google.gson.JsonParseException e) {
            throw new IOException("Invalid CATS summary report: " + resolved, e);
        }
    }

    private static void requireProblems(CatsTestReport report, String file) {
        if (report.getProblemKeyVersion() != Problem.KEY_VERSION || report.getProblems() == null) {
            throw new UnsupportedReportException("%s was written by a CATS version without problems (problemKeyVersion %d, expected %d); re-run it with this CATS version"
                    .formatted(file, report.getProblemKeyVersion(), Problem.KEY_VERSION));
        }
    }

    static Path resolveSummaryFile(Path path) throws IOException {
        if (path == null) {
            throw new IOException("Report path must not be null");
        }
        Path candidate = Files.isDirectory(path) ? path.resolve(SUMMARY_FILE_NAME) : path;
        if (!Files.isRegularFile(candidate)) {
            throw new IOException("CATS summary report not found at: " + candidate);
        }
        return candidate;
    }

    public static ComparisonResult compareReports(Path baseline, Path target, boolean failOnNewWarnings) throws IOException {
        return compare(new Reports(baseline.toString(), target.toString(), loadReport(baseline), loadReport(target)), failOnNewWarnings);
    }

    public static ComparisonResult compareWithCurrent(Path baseline, CatsTestReport current, boolean failOnNewWarnings) throws IOException {
        return compare(new Reports(baseline.toString(), "current run", loadReport(baseline), current), failOnNewWarnings);
    }

    public static ComparisonResult compare(Reports reports, boolean failOnNewWarnings) {
        requireProblems(reports.baseline(), reports.baselineName());
        requireProblems(reports.target(), reports.targetName());
        Map<OccurrenceKey, Side> baseline = occurrences(reports.baseline());
        Map<OccurrenceKey, Side> target = occurrences(reports.target());
        List<OccurrenceDelta> exact = target.keySet().stream().filter(baseline::containsKey)
                .map(key -> delta(State.UNCHANGED, baseline.get(key), target.get(key), MatchedBy.ID)).toList();
        Map<OccurrenceKey, Side> baselineOnly = difference(baseline, target.keySet());
        Map<OccurrenceKey, Side> targetOnly = difference(target, baseline.keySet());
        List<OccurrenceDelta> patterns = matchPatterns(baselineOnly, targetOnly);
        List<OccurrenceDelta> remaining = remaining(reports, baselineOnly, targetOnly);
        return result(reports, Stream.concat(Stream.concat(exact.stream(), patterns.stream()), remaining.stream()).toList(), failOnNewWarnings);
    }

    private static List<OccurrenceDelta> remaining(Reports reports, Map<OccurrenceKey, Side> baseline, Map<OccurrenceKey, Side> target) {
        Set<String> baselineFuzzers = ran(reports.baseline());
        Set<String> targetFuzzers = ran(reports.target());
        return Stream.concat(baseline.values().stream().map(side -> unmatched(side, targetFuzzers, false)),
                target.values().stream().map(side -> unmatched(side, baselineFuzzers, true))).toList();
    }

    private static ComparisonResult result(Reports reports, List<OccurrenceDelta> deltas, boolean failOnNewWarnings) {
        List<OccurrenceDelta> added = inState(deltas, State.NEW);
        boolean regression = added.stream().anyMatch(delta -> delta.isError() || failOnNewWarnings);
        return new ComparisonResult(reports.baselineName(), reports.targetName(), Problem.KEY_VERSION, regression,
                added, inState(deltas, State.FIXED), inState(deltas, State.UNCHANGED), inState(deltas, State.NOT_COMPARABLE),
                (int) deltas.stream().filter(delta -> delta.matchedBy() == MatchedBy.PATTERN).count());
    }

    private static List<OccurrenceDelta> inState(List<OccurrenceDelta> deltas, State state) {
        return deltas.stream().filter(delta -> delta.state() == state).toList();
    }

    private static Map<OccurrenceKey, Side> occurrences(CatsTestReport report) {
        return Optional.ofNullable(report.getProblems()).orElse(List.of()).stream()
                .flatMap(problem -> problem.occurrences().stream().flatMap(occurrence -> sides(problem, occurrence)))
                .collect(Collectors.toMap(Side::key, Function.identity(), (first, _) -> first, LinkedHashMap::new));
    }

    private static Stream<Side> sides(Problem problem, Problem.Occurrence occurrence) {
        if (problem.kind() != ProblemKind.ACCEPTED_REQUEST || occurrence.fields().isEmpty()) {
            return Stream.of(new Side(new OccurrenceKey(problem.id(), occurrence.operation(), ""), problem, occurrence, occurrence.testIds().size()));
        }
        return occurrence.fields().stream().map(field -> new Side(
                new OccurrenceKey(problem.id(), occurrence.operation(), field.field()), problem, occurrence, field.tests()));
    }

    private static Map<OccurrenceKey, Side> difference(Map<OccurrenceKey, Side> sides, Set<OccurrenceKey> excluded) {
        return sides.values().stream().filter(side -> !excluded.contains(side.key()))
                .collect(Collectors.toMap(Side::key, Function.identity(), (first, _) -> first, LinkedHashMap::new));
    }

    private static List<OccurrenceDelta> matchPatterns(Map<OccurrenceKey, Side> baseline, Map<OccurrenceKey, Side> target) {
        Map<OccurrenceKey, Side> matched = new LinkedHashMap<>();
        for (Side right : target.values()) {
            baseline.values().stream().filter(left -> left.matches(right)).findFirst()
                    .ifPresent(left -> matched.put(right.key(), baseline.remove(left.key())));
        }
        return matched.entrySet().stream()
                .map(entry -> delta(State.UNCHANGED, entry.getValue(), target.remove(entry.getKey()), MatchedBy.PATTERN)).toList();
    }

    private static Set<String> ran(CatsTestReport report) {
        return Optional.ofNullable(report.getTestCases()).orElse(List.of()).stream().filter(Objects::nonNull)
                .map(CatsTestCaseSummary::getFuzzer).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static OccurrenceDelta unmatched(Side side, Set<String> otherFuzzers, boolean target) {
        State state = side.occurrence().fuzzers().stream().noneMatch(otherFuzzers::contains)
                ? State.NOT_COMPARABLE : target ? State.NEW : State.FIXED;
        return delta(state, target ? null : side, target ? side : null, null);
    }

    private static OccurrenceDelta delta(State state, Side baseline, Side target, MatchedBy matchedBy) {
        Side shown = target == null ? baseline : target;
        return new OccurrenceDelta(state, shown.problem().id(), shown.problem().kind(), shown.problem().level(), shown.problem().title(),
                shown.key().operation(), shown.key().field(), shown.occurrence().statuses(),
                baseline == null ? 0 : baseline.tests(), target == null ? 0 : target.tests(), matchedBy);
    }

    public static void renderToConsole(PrettyLogger logger, ComparisonResult result) {
        renderSection(logger, "New", result.newOccurrences());
        renderSection(logger, "Fixed", result.fixedOccurrences());
        renderSection(logger, "Not comparable", result.notComparable());
        renderSection(logger, "Unchanged (count changed)", result.unchangedOccurrences().stream()
                .filter(delta -> delta.baselineTests() != delta.targetTests()).toList());
        logger.noFormat("Baseline Verdict: {}", result.regression() ? "REGRESSION_DETECTED" : "PASS");
    }

    private static void renderSection(PrettyLogger logger, String name, List<OccurrenceDelta> deltas) {
        logger.noFormat("{} ({})", name, deltas.size());
        deltas.forEach(delta -> logger.noFormat("{} {} {}{} {} (baseline {} -> target {} tests)",
                delta.state(), delta.problemId(), delta.operation(), delta.field().isEmpty() ? "" : " " + delta.field(),
                delta.title(), delta.baselineTests(), delta.targetTests()));
    }

    /** Named inputs also appear verbatim in the JSON result. */
    public record Reports(String baselineName, String targetName, CatsTestReport baseline, CatsTestReport target) {
    }

    private record Side(OccurrenceKey key, Problem problem, Problem.Occurrence occurrence, int tests) {
        boolean matches(Side other) {
            return key.operation().equals(other.key.operation()) && occurrence.statuses().equals(other.occurrence.statuses())
                    && ServerMessagePattern.matches(problem, other.problem);
        }
    }
}
