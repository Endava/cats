package com.endava.cats.command;

import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.Problem;
import com.endava.cats.model.ProblemKind;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.endava.cats.report.ComparisonFixtures.occurrence;
import static com.endava.cats.report.ComparisonFixtures.problem;
import static com.endava.cats.report.ComparisonFixtures.report;

@QuarkusTest
class CompareCommandTest {
    @TempDir
    Path directory;

    @Test
    void shouldPrintEveryStateAndCountChangesPerOccurrence() throws Exception {
        Problem a = problem("a", ProblemKind.STATUS_CODE, 2);
        Problem b = problem("b", ProblemKind.STATUS_CODE, 1);
        Problem c = problem("c", ProblemKind.ACCEPTED_REQUEST, 1).toBuilder()
                .occurrences(List.of(occurrence("POST /orders", 1, List.of(new Problem.FieldCount("body.note", 1))))).build();
        Problem d = problem("d", ProblemKind.SERVER_ERROR, 1).toBuilder().occurrences(List.of(new Problem.Occurrence(
                "GET /orders", List.of(), List.of("Test 4"), List.of(500), List.of("Absent")))).build();
        CompareCommand command = command(report(a, b), report(problem("a", ProblemKind.STATUS_CODE, 4), c, d));
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);
        ReflectionTestUtils.setField(command, "logger", logger);
        command.run();
        Assertions.assertThat(command.getExitCode()).isEqualTo(1);
        Mockito.verify(logger).noFormat("{} ({})", "New", 1);
        Mockito.verify(logger).noFormat("{} ({})", "Fixed", 1);
        Mockito.verify(logger).noFormat("{} ({})", "Not comparable", 1);
        Mockito.verify(logger).noFormat("{} ({})", "Unchanged (test counts changed; 1 total unchanged)", 1);
        Mockito.verify(logger).noFormat("Occurrences: {} new | {} fixed | {} unchanged | {} not comparable", 1, 1, 1, 1);
        verifyLine(logger, c, " [body.note]", List.of("0 tests", "1 test"));
        verifyLine(logger, b, "", List.of("1 test", "0 tests"));
        verifyLine(logger, a, "", List.of("2 tests", "4 tests"));
        Mockito.verify(logger).noFormat("  {} [{}] {}{}", "d", "ERROR", "GET /orders", "");
        Mockito.verify(logger).noFormat("    Baseline: {} | Target: {}", "not observed", "1 test");
        Mockito.verify(logger).noFormat("    Coverage: no saved {} results for {}.", "baseline", "Absent");
        Mockito.verify(logger).noFormat("New regressions detected.");
    }

    @Test
    void shouldOutputExactlyOneJsonResultWithTheApprovedFields() throws Exception {
        CompareCommand command = command(report(problem("a", ProblemKind.SERVER_ERROR, 2)),
                report(problem("b", ProblemKind.SERVER_ERROR, 3).toBuilder().errorTemplate("Cluster <*> cannot be found.").build()));
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);
        ReflectionTestUtils.setField(command, "logger", logger);
        command.json = true;
        command.run();
        ArgumentCaptor<String> output = ArgumentCaptor.forClass(String.class);
        Mockito.verify(logger).noFormat(output.capture());
        Mockito.verifyNoMoreInteractions(logger);
        JsonObject json = JsonParser.parseString(output.getValue()).getAsJsonObject();
        Assertions.assertThat(json.keySet()).containsExactlyInAnyOrder("baseline", "target", "problemKeyVersion", "regression",
                "newOccurrences", "fixedOccurrences", "unchangedOccurrences", "notComparable", "matchedByPattern");
        Assertions.assertThat(json.get("regression").getAsBoolean()).isFalse();
        Assertions.assertThat(json.get("problemKeyVersion").getAsInt()).isEqualTo(1);
        Assertions.assertThat(json.get("matchedByPattern").getAsInt()).isEqualTo(1);
        JsonObject delta = json.getAsJsonArray("unchangedOccurrences").get(0).getAsJsonObject();
        Assertions.assertThat(delta.keySet()).containsExactlyInAnyOrder("state", "problemId", "kind", "level", "title", "operation", "field",
                "statuses", "baselineTests", "targetTests", "matchedBy");
        Assertions.assertThat(delta.get("matchedBy").getAsString()).isEqualTo("PATTERN");
        Assertions.assertThat(command.getExitCode()).isZero();
    }

    @Test
    void shouldExitZeroForCountChangesFixesAndNotComparable() throws Exception {
        Problem a = problem("a", ProblemKind.STATUS_CODE, 2);
        CompareCommand changed = command(report(a), report(problem("a", ProblemKind.STATUS_CODE, 9)));
        changed.run();
        Assertions.assertThat(changed.getExitCode()).isZero();
        CompareCommand fixed = command(report(a), report());
        fixed.run();
        Assertions.assertThat(fixed.getExitCode()).isZero();
        var absent = CatsTestReport.builder().problemKeyVersion(1).problems(List.of()).testCases(List.of()).build();
        CompareCommand incomparable = command(absent, report(a));
        incomparable.run();
        Assertions.assertThat(incomparable.getExitCode()).isZero();
    }

    @Test
    void shouldFailOnNewErrorsAndOptionallyWarnings() throws Exception {
        CompareCommand errors = command(report(), report(problem("a", ProblemKind.STATUS_CODE, 1)));
        errors.run();
        Assertions.assertThat(errors.getExitCode()).isEqualTo(1);
        CompareCommand warnings = command(report(), report(problem("a", ProblemKind.STATUS_CODE, 1).toBuilder().level("warn").build()));
        warnings.run();
        Assertions.assertThat(warnings.getExitCode()).isZero();
        warnings.failOnNewWarnings = true;
        warnings.run();
        Assertions.assertThat(warnings.getExitCode()).isEqualTo(1);
    }

    @Test
    void shouldExitTwoForMissingInvalidOrIncompatibleReports() throws Exception {
        for (String invalid : List.of("", "not json", "null", "{\"testCases\":[]}", "{\"problemKeyVersion\":2,\"problems\":[]}")) {
            CompareCommand command = command(report(), report());
            Files.writeString(command.target.toPath(), invalid);
            command.run();
            Assertions.assertThat(command.getExitCode()).as(invalid).isEqualTo(2);
        }
        CompareCommand missing = command(report(), report());
        Files.delete(missing.baseline.toPath());
        missing.run();
        Assertions.assertThat(missing.getExitCode()).isEqualTo(2);
        Assertions.assertThat(new CommandLine(new CompareCommand()).execute()).isEqualTo(2);
    }

    @Test
    void shouldAcceptReportDirectoriesAndRejectRemovedModes() throws Exception {
        CompareCommand command = command(report(), report());
        Path folder = Files.createDirectory(directory.resolve("report"));
        Files.copy(command.baseline.toPath(), folder.resolve("cats-summary-report.json"));
        command.baseline = folder.toFile();
        command.run();
        Assertions.assertThat(command.getExitCode()).isZero();
        Assertions.assertThat(new CommandLine(new CompareCommand()).execute("--compareBy", "count")).isEqualTo(2);
        Assertions.assertThat(new CommandLine(new CompareCommand()).execute("--compareScope", "operation")).isEqualTo(2);
    }

    private CompareCommand command(CatsTestReport baseline, CatsTestReport target) throws Exception {
        CompareCommand command = new CompareCommand();
        command.baseline = directory.resolve("base.json").toFile();
        command.target = directory.resolve("target.json").toFile();
        Files.writeString(command.baseline.toPath(), JsonUtils.GSON.toJson(baseline));
        Files.writeString(command.target.toPath(), JsonUtils.GSON.toJson(target));
        return command;
    }

    private static void verifyLine(PrettyLogger logger, Problem problem, String field, List<String> counts) {
        Mockito.verify(logger).noFormat("  {} [{}] {}{}", problem.id(), "ERROR", "POST /orders", field);
        Mockito.verify(logger).noFormat("    {}", problem.title());
        Mockito.verify(logger).noFormat("    Baseline: {} | Target: {}", counts.getFirst(), counts.getLast());
    }
}
