package com.endava.cats.command.triage;

import com.endava.cats.exception.UnsupportedReportException;
import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class ReportCompletenessTest {
    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "not JSON", "{}", "{\"testId\":\"Test 1\",\"request\":{}}"})
    void shouldRejectMissingUnreadableOrIncompleteReferencedEvidence(String content, @TempDir Path folder) throws IOException {
        writeReport(folder);
        if ("MISSING".equals(content)) {
            Files.delete(folder.resolve("Test1.json"));
        } else {
            Files.writeString(folder.resolve("Test1.json"), content);
        }
        TriageEngine engine = engine();

        assertThatThrownBy(() -> engine.triage(folder, null)).isInstanceOf(UnsupportedReportException.class)
                .hasMessageContainingAll("Incomplete report", "Test1.json", "re-run CATS");
    }

    @Test
    void shouldAcceptSavedFailuresWhenSuccessfulTestsWereSkippedFromReporting(@TempDir Path folder) throws IOException {
        writeReport(folder);
        var summary = JsonUtils.parseAsJsonElement(Files.readString(folder.resolve(TriageEngine.SUMMARY_FILE))).getAsJsonObject();
        summary.addProperty("skippedFromReporting", 500);
        summary.addProperty("totalRequests", 501);
        Files.writeString(folder.resolve(TriageEngine.SUMMARY_FILE), summary.toString());

        TriageReport report = engine().triage(folder, null);

        assertThat(report.totalTestsScanned()).isEqualTo(1);
        assertThat(report.totalProblems()).isEqualTo(1);
    }

    @Test
    void shouldValidateProblemReferencesEvenIfTheSummaryTestListIsEmpty(@TempDir Path folder) throws IOException {
        writeReport(folder);
        var summary = JsonUtils.parseAsJsonElement(Files.readString(folder.resolve(TriageEngine.SUMMARY_FILE))).getAsJsonObject();
        summary.add("testCases", JsonUtils.GSON.toJsonTree(List.of()));
        Files.writeString(folder.resolve(TriageEngine.SUMMARY_FILE), summary.toString());
        Files.delete(folder.resolve("Test1.json"));
        TriageEngine engine = engine();

        assertThatThrownBy(() -> engine.triage(folder, null)).isInstanceOf(UnsupportedReportException.class)
                .hasMessageContaining("Test1.json");
    }

    @Test
    void shouldAcceptATrulyEmptyReport(@TempDir Path folder) throws IOException {
        TriageReportFixtures.writeSummary(folder);

        assertThat(engine().triage(folder, null).problems()).isEmpty();
    }

    private static TriageEngine engine() {
        return new TriageEngine(new TriageOptions(false, false, null, Map.of(), _ -> null));
    }

    private static void writeReport(Path folder) throws IOException {
        var test = TriageFixtures.rejected("Probe", "2XX", "/items", "{}", 500, "Failure");
        Files.writeString(folder.resolve("Test1.json"), JsonUtils.GSON.toJson(test.testCase()));
        TriageReportFixtures.writeSummary(folder);
    }
}
