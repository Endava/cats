package com.endava.cats.tui;

import com.endava.cats.tui.event.CatsExecutionEvent;
import com.endava.cats.tui.model.RunConfigurationSnapshot;
import com.endava.cats.tui.model.RunSummarySnapshot;
import com.endava.cats.tui.model.TestResultSnapshot;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@QuarkusTest
class CatsTuiStateTest {
    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Test
    void shouldTrackLiveProgressAndUseFinalSummary() {
        CatsTuiState state = new CatsTuiState();
        state.accept(new CatsExecutionEvent.SessionStarted(NOW, "cats", "1", "today", "test"));
        state.accept(new CatsExecutionEvent.ConfigurationLoaded(NOW,
                new RunConfigurationSnapshot("1", "openapi.yml", "http://localhost", List.of("GET"),
                        2, 4, 2, 2)));
        state.accept(new CatsExecutionEvent.PathStarted(NOW, "/pets"));
        state.accept(new CatsExecutionEvent.FuzzerStarted(NOW, "MissingFields", "/pets", "POST"));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("success")));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("warning")));
        state.accept(new CatsExecutionEvent.PathCompleted(NOW, "/pets"));

        Assertions.assertThat(state.pathProgress()).isEqualTo(0.5);
        Assertions.assertThat(state.pathProgressLabel()).isEqualTo("1 / 2 paths");
        Assertions.assertThat(state.currentPath()).isEqualTo("/pets");
        Assertions.assertThat(state.currentFuzzer()).isEqualTo("MissingFields (POST)");
        Assertions.assertThat(state.completedTests()).isEqualTo(2);
        Assertions.assertThat(state.success()).isEqualTo(1);
        Assertions.assertThat(state.warnings()).isEqualTo(1);

        state.accept(new CatsExecutionEvent.SessionCompleted(NOW.plusSeconds(5),
                new RunSummarySnapshot(20, 18, 10, 3, 5, 2, 2, 0, 0, Map.of(200, 10),
                        Map.of("/pets", 5L), false, "Default: fail on any error")));

        Assertions.assertThat(state.running()).isFalse();
        Assertions.assertThat(state.status()).isEqualTo("Finished");
        Assertions.assertThat(state.success()).isEqualTo(10);
        Assertions.assertThat(state.warnings()).isEqualTo(3);
        Assertions.assertThat(state.errors()).isEqualTo(5);
        Assertions.assertThat(state.totalRequests()).isEqualTo(20);
        Assertions.assertThat(state.reportedResults()).isEqualTo(18);
        Assertions.assertThat(state.skipped()).isEqualTo(4);
        Assertions.assertThat(state.skippedFromReporting()).isEqualTo(2);
        Assertions.assertThat(state.topFailingPaths()).contains("/pets (5)");
        Assertions.assertThat(state.qualityGatePassed()).isFalse();
        Assertions.assertThat(state.qualityGateDescription()).isEqualTo("Default: fail on any error");
        Assertions.assertThat(state.elapsed()).isEqualTo("00:00:05");

        state.handleKey(KeyEvent.ofChar('3'));
        Assertions.assertThat(state.screen()).isEqualTo(CatsTuiState.Screen.SUMMARY);
    }

    @Test
    void shouldExposeSessionFailure() {
        CatsTuiState state = new CatsTuiState();
        state.accept(new CatsExecutionEvent.SessionFailed(NOW, "Contract cannot be read"));

        Assertions.assertThat(state.running()).isFalse();
        Assertions.assertThat(state.status()).isEqualTo("Failed");
        Assertions.assertThat(state.failureMessage()).isEqualTo("Contract cannot be read");
    }

    @Test
    void shouldExposeSessionCancellation() {
        CatsTuiState state = new CatsTuiState();
        state.accept(new CatsExecutionEvent.SessionCancelled(NOW, "Execution cancelled by user"));

        Assertions.assertThat(state.running()).isFalse();
        Assertions.assertThat(state.status()).isEqualTo("Cancelled");
        Assertions.assertThat(state.failureMessage()).isEqualTo("Execution cancelled by user");
    }

    @Test
    void shouldFilterNavigateAndOpenTestDetails() {
        CatsTuiState state = new CatsTuiState();
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("1", "success", 200, 10)));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("2", "warning", 422, 20)));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("3", "error", 500, 90)));

        state.handleKey(KeyEvent.ofChar('2'));
        Assertions.assertThat(state.screen()).isEqualTo(CatsTuiState.Screen.RESULTS);
        Assertions.assertThat(state.filteredResults()).hasSize(3);
        Assertions.assertThat(state.selectedTest().id()).isEqualTo("1");

        state.handleKey(KeyEvent.ofKey(KeyCode.DOWN));
        Assertions.assertThat(state.selectedTest().id()).isEqualTo("2");

        state.handleKey(KeyEvent.ofChar('e'));
        Assertions.assertThat(state.filter()).isEqualTo(CatsTuiState.ResultFilter.ERROR);
        Assertions.assertThat(state.filteredResults()).extracting(TestResultSnapshot::id).containsExactly("3");

        state.updateViewport(80, 100);
        state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
        Assertions.assertThat(state.screen()).isEqualTo(CatsTuiState.Screen.DETAIL);
        Assertions.assertThat(state.visibleDetailLines()).anyMatch(line -> line.startsWith("Scenario:"))
                .anyMatch(line -> line.startsWith("Expected Result:"))
                .anyMatch(line -> line.startsWith("Result Reason:"))
                .anyMatch(line -> line.startsWith("Result Details:"))
                .anyMatch(line -> line.startsWith("Test Trace Id:"))
                .anyMatch(line -> line.startsWith("Contract Path:"))
                .anyMatch(line -> line.startsWith("Full Request Path:"))
                .anyMatch(line -> line.equals("REQUEST DETAILS"))
                .anyMatch(line -> line.startsWith("Http Method:"))
                .anyMatch(line -> line.startsWith("HTTP Response Code:") && line.contains("Response Time:"))
                .anyMatch(line -> line.startsWith("Content Type:") && line.contains("Content Length:"))
                .anyMatch(line -> line.startsWith("CATS Replay:"));
        Assertions.assertThat(state.visibleDetailLines()).noneMatch(line -> line.indexOf('\u001b') >= 0);
        Assertions.assertThat(state.visibleDetailLines()).allMatch(line -> line.length() <= 76);

        state.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE));
        Assertions.assertThat(state.screen()).isEqualTo(CatsTuiState.Screen.RESULTS);
    }

    @Test
    void shouldBuildCategoryResponseAndTimingSummary() {
        CatsTuiState state = new CatsTuiState();
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("1", "success", 200, 10)));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("2", "error", 500, 90)));

        Assertions.assertThat(state.averageResponseTime()).isEqualTo(50);
        Assertions.assertThat(state.maximumResponseTime()).isEqualTo(90);
        Assertions.assertThat(state.responseCodeSummary()).contains("200: 1", "500: 1");
        Assertions.assertThat(state.categorySummary())
                .contains("fuzzer: 2 tests, 1 Errors, 0 Skipped, Average Response Time 50ms");
        Assertions.assertThat(state.fuzzerStatistics(5)).singleElement().satisfies(row -> {
            Assertions.assertThat(row.fuzzer()).isEqualTo("fuzzer");
            Assertions.assertThat(row.total()).isEqualTo(2);
            Assertions.assertThat(row.success()).isEqualTo(1);
            Assertions.assertThat(row.errors()).isEqualTo(1);
        });
    }

    @Test
    void shouldUseAvailableHeightAndScrollThroughEveryFuzzer() {
        CatsTuiState state = new CatsTuiState();
        for (int index = 1; index <= 6; index++) {
            state.accept(new CatsExecutionEvent.TestCompleted(NOW,
                    test(String.valueOf(index), "success", 200, 10, "fuzzer-" + index)));
        }

        state.updateViewport(80, 24);
        Assertions.assertThat(state.fuzzerPageSize(CatsTuiState.Screen.OVERVIEW)).isEqualTo(1);
        Assertions.assertThat(state.fuzzerStatistics(state.fuzzerOffset(),
                state.fuzzerPageSize(CatsTuiState.Screen.OVERVIEW))).hasSize(1);

        state.handleKey(KeyEvent.ofKey(KeyCode.DOWN));
        Assertions.assertThat(state.fuzzerOffset()).isEqualTo(1);
        state.handleKey(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
        Assertions.assertThat(state.fuzzerOffset()).isEqualTo(2);

        state.handleKey(KeyEvent.ofChar('3'));
        Assertions.assertThat(state.fuzzerPageSize(CatsTuiState.Screen.SUMMARY)).isEqualTo(2);
        Assertions.assertThat(state.fuzzerOffset()).isEqualTo(2);
        state.handleKey(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
        Assertions.assertThat(state.fuzzerOffset()).isEqualTo(4);
        Assertions.assertThat(state.fuzzerStatistics(state.fuzzerOffset(),
                state.fuzzerPageSize(CatsTuiState.Screen.SUMMARY))).hasSize(2);

        state.updateViewport(80, 40);
        Assertions.assertThat(state.fuzzerStatistics(state.fuzzerOffset(),
                state.fuzzerPageSize(CatsTuiState.Screen.SUMMARY))).hasSize(6);
        Assertions.assertThat(state.fuzzerOffset()).isZero();
    }

    @Test
    void shouldUseTheAvailableTerminalHeightAndWidthForDetails() {
        CatsTuiState state = new CatsTuiState();
        state.updateViewport(120, 40);
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("1", "error", 500, 90)));
        state.handleKey(KeyEvent.ofChar('2'));
        state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));

        Assertions.assertThat(state.visibleDetailLines()).hasSizeGreaterThan(20)
                .allMatch(line -> line.length() <= 116);
    }

    @Test
    void shouldSearchRetainedTestsWithoutTriggeringNavigationKeys() {
        CatsTuiState state = new CatsTuiState();
        state.accept(new CatsExecutionEvent.TestCompleted(NOW,
                test("1", "error", 500, 90, "SchemaFuzzer", "/pets", "Schema validation failed", "invalid pet")));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW,
                test("2", "warning", 422, 20, "BoundaryFuzzer", "/orders", "Unexpected code", "large order")));
        state.handleKey(KeyEvent.ofChar('2'));
        state.handleKey(KeyEvent.ofChar('/'));
        "schema".chars().forEach(character -> state.handleKey(KeyEvent.ofChar(character)));

        Assertions.assertThat(state.searchEditing()).isTrue();
        Assertions.assertThat(state.filteredResults()).extracting(TestResultSnapshot::id).containsExactly("1");
        Assertions.assertThat(state.status()).isEqualTo("Starting CATS");

        state.handleKey(KeyEvent.ofChar(0x1F63A));
        state.handleKey(KeyEvent.ofKey(KeyCode.BACKSPACE));
        Assertions.assertThat(state.searchQuery()).isEqualTo("schema");
        state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
        Assertions.assertThat(state.searchEditing()).isFalse();
        state.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE));
        Assertions.assertThat(state.searchQuery()).isEmpty();
        Assertions.assertThat(state.screen()).isEqualTo(CatsTuiState.Screen.RESULTS);
        state.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE));
        Assertions.assertThat(state.screen()).isEqualTo(CatsTuiState.Screen.OVERVIEW);
    }

    @Test
    void shouldAggregateIssuesAndPathsBeyondRetainedDetailsAndDrillDown() {
        CatsTuiState state = new CatsTuiState(2);
        state.accept(new CatsExecutionEvent.TestCompleted(NOW,
                test("1", "error", 500, 90, "First", "/pets", "Schema failed", "one")));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW,
                test("2", "warning", 422, 20, "Second", "/pets", "Schema failed", "two")));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW,
                test("3", "error", 503, 70, "Third", "/orders", "Unavailable", "three")));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW,
                test("4", "warning", 0, 0, "Contract", "/contract", "Contract issue", "four")));

        Assertions.assertThat(state.issueStatistics()).hasSize(3);
        Assertions.assertThat(state.issueStatistics()).filteredOn(row -> row.reason().equals("Schema failed"))
                .singleElement().satisfies(row -> {
                    Assertions.assertThat(row.total()).isEqualTo(2);
                    Assertions.assertThat(row.errors()).isEqualTo(1);
                    Assertions.assertThat(row.warnings()).isEqualTo(1);
                    Assertions.assertThat(row.pathCount()).isEqualTo(1);
                });
        Assertions.assertThat(state.issueStatistics()).filteredOn(row -> row.reason().equals("Contract issue"))
                .singleElement().extracting(CatsTuiState.IssueStatisticsRow::responseCodes).isEqualTo("n/a");
        Assertions.assertThat(state.pathStatistics()).filteredOn(row -> row.path().equals("/pets"))
                .singleElement().satisfies(row -> Assertions.assertThat(row.total()).isEqualTo(2));

        state.handleKey(KeyEvent.ofChar('2'));
        state.handleKey(KeyEvent.ofChar('s'));
        state.handleKey(KeyEvent.ofChar('4'));
        state.handleKey(KeyEvent.ofKey(KeyCode.DOWN));
        state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
        Assertions.assertThat(state.screen()).isEqualTo(CatsTuiState.Screen.RESULTS);
        Assertions.assertThat(state.filter()).isEqualTo(CatsTuiState.ResultFilter.ALL);
        Assertions.assertThat(state.filteredResults()).allMatch(test -> test.resultReason().equals("Contract issue"));
        state.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE));
        Assertions.assertThat(state.screen()).isEqualTo(CatsTuiState.Screen.ISSUES);

        state.handleKey(KeyEvent.ofChar('5'));
        state.handleKey(KeyEvent.ofKey(KeyCode.DOWN));
        state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
        Assertions.assertThat(state.resultsContext()).startsWith("Path: ");
        Assertions.assertThat(state.filteredResults()).allMatch(test ->
                test.contractPath().equals(state.filteredResults().getFirst().contractPath()));
    }

    @Test
    void shouldOrderSlowTestsAndReturnToPerformanceScreenFromDetails() {
        CatsTuiState state = new CatsTuiState();
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("fast", "success", 200, 5)));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("slow", "error", 500, 900)));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("skip", "skipped", 0, 5000)));

        List<TestResultSnapshot> initialRanking = state.slowestResults();
        Assertions.assertThat(initialRanking).extracting(TestResultSnapshot::id)
                .containsExactly("slow", "fast");
        Assertions.assertThat(state.slowestResults()).isSameAs(initialRanking);
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("slowest", "warning", 429, 1200)));
        Assertions.assertThat(state.slowestResults()).isNotSameAs(initialRanking)
                .extracting(TestResultSnapshot::id).containsExactly("slowest", "slow", "fast");
        state.handleKey(KeyEvent.ofChar('2'));
        state.handleKey(KeyEvent.ofChar('e'));
        state.handleKey(KeyEvent.ofChar('6'));
        state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
        Assertions.assertThat(state.screen()).isEqualTo(CatsTuiState.Screen.DETAIL);
        Assertions.assertThat(state.filter()).isEqualTo(CatsTuiState.ResultFilter.ALL);
        Assertions.assertThat(state.selectedTest().id()).isEqualTo("slowest");
        state.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE));
        Assertions.assertThat(state.screen()).isEqualTo(CatsTuiState.Screen.SLOWEST);
    }

    @Test
    void shouldPageThroughLongIssuePathAndSlowestTables() {
        CatsTuiState state = new CatsTuiState();
        state.updateViewport(80, 24);
        for (int index = 0; index < 20; index++) {
            state.accept(new CatsExecutionEvent.TestCompleted(NOW,
                    test("id-%02d".formatted(index), "error", 500, index, "fuzzer",
                            "/path-%02d".formatted(index), "issue-%02d".formatted(index), "scenario")));
        }

        state.handleKey(KeyEvent.ofChar('4'));
        state.handleKey(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
        state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
        Assertions.assertThat(state.resultsContext()).isEqualTo("Result Reason: issue-14");

        state.handleKey(KeyEvent.ofChar('5'));
        state.handleKey(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
        state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
        Assertions.assertThat(state.resultsContext()).isEqualTo("Path: /path-14");

        state.handleKey(KeyEvent.ofChar('6'));
        state.handleKey(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
        state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
        Assertions.assertThat(state.selectedTest().id()).isEqualTo("id-05");
    }

    @Test
    void shouldUseConfiguredPathsForProgress() {
        CatsTuiState state = new CatsTuiState();
        state.accept(new CatsExecutionEvent.ConfigurationLoaded(NOW,
                new RunConfigurationSnapshot("1", "openapi.yml", "http://localhost", List.of("GET"),
                        2, 20, 2, 10)));
        state.accept(new CatsExecutionEvent.PathCompleted(NOW, "/pets"));

        Assertions.assertThat(state.pathProgress()).isEqualTo(0.5);
        Assertions.assertThat(state.pathProgressLabel()).isEqualTo("1 / 2 paths");
    }

    @Test
    void shouldTrackAndFilterSkippedResultsWithoutPollutingTimingOrCodes() {
        CatsTuiState state = new CatsTuiState();
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("1", "success", 200, 10)));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("2", "skip_reporting", 503, 90)));

        state.handleKey(KeyEvent.ofChar('2'));
        state.handleKey(KeyEvent.ofChar('i'));

        Assertions.assertThat(state.filteredResults()).extracting(TestResultSnapshot::id).containsExactly("2");
        Assertions.assertThat(state.skipped()).isEqualTo(1);
        Assertions.assertThat(state.skippedFromReporting()).isEqualTo(1);
        Assertions.assertThat(state.averageResponseTime()).isEqualTo(10);
        Assertions.assertThat(state.responseCodeSummary()).contains("200: 1").doesNotContain("503");
    }

    @Test
    void shouldBoundRetainedDetailsWhileKeepingCompleteCounts() {
        CatsTuiState state = new CatsTuiState(2);
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("1", "success", 200, 10)));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("2", "warning", 400, 20)));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("3", "error", 500, 30)));

        Assertions.assertThat(state.completedTests()).isEqualTo(3);
        Assertions.assertThat(state.retainedResults()).isEqualTo(2);
        Assertions.assertThat(state.discardedResults()).isEqualTo(1);
        Assertions.assertThat(state.filteredResults()).extracting(TestResultSnapshot::id).containsExactly("2", "3");
    }

    @Test
    void shouldKeepTheSameSelectionWhenAnOlderResultIsDiscarded() {
        CatsTuiState state = new CatsTuiState(3);
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("1", "success", 200, 10)));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("2", "warning", 400, 20)));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("3", "error", 500, 30)));
        state.handleKey(KeyEvent.ofChar('2'));
        state.handleKey(KeyEvent.ofKey(KeyCode.DOWN));

        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("4", "success", 201, 40)));

        Assertions.assertThat(state.selectedTest().id()).isEqualTo("2");
        Assertions.assertThat(state.selectedIndex()).isZero();
    }

    @Test
    void shouldSanitizeControlCharactersOutsideTheDetailView() {
        CatsTuiState state = new CatsTuiState();
        state.accept(new CatsExecutionEvent.PathStarted(NOW, "/pets\u001b[2J\nnext"));
        state.accept(new CatsExecutionEvent.FuzzerStarted(NOW, "fuzzer\u0007", "/pets", "G\tET"));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("1", "success-error", 200, 10)));

        Assertions.assertThat(state.currentPath()).doesNotContain("\u001b", "\n");
        Assertions.assertThat(state.currentFuzzer()).doesNotContain("\u0007", "\t");
        Assertions.assertThat(state.errors()).isZero();
        Assertions.assertThat(state.success()).isZero();
    }

    private static TestResultSnapshot test(String result) {
        return test("1", result, 200, 10);
    }

    private static TestResultSnapshot test(String id, String result, int responseCode, long responseTime) {
        return test(id, result, responseCode, responseTime, "fuzzer");
    }

    private static TestResultSnapshot test(String id, String result, int responseCode, long responseTime, String fuzzer) {
        return test(id, result, responseCode, responseTime, fuzzer, "/pets", "reason",
                "scenario\u001b" + "x".repeat(100));
    }

    private static TestResultSnapshot test(String id, String result, int responseCode, long responseTime,
                                           String fuzzer, String path, String reason, String scenario) {
        return new TestResultSnapshot(id, "trace", scenario, "expected", result, reason, "details", "",
                fuzzer, path, path, path, "http://localhost", true,
                new TestResultSnapshot.RequestSnapshot("GET", path, "now", "", List.of()),
                new TestResultSnapshot.ResponseSnapshot(responseCode, "GET", responseTime, 0, 0, 0,
                        "application/json", "{}", List.of()),
                "cats replay");
    }
}
