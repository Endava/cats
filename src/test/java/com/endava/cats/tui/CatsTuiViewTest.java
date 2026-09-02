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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@QuarkusTest
class CatsTuiViewTest {
    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");

    @Test
    void shouldRenderEveryEmptyScreenAndTerminalMessage() {
        CatsTuiState state = new CatsTuiState();
        assertRenders(state);

        for (char screen : new char[]{'2', '3', '4', '5', '6', '1'}) {
            state.handleKey(KeyEvent.ofChar(screen));
            assertRenders(state);
        }

        ReflectionTestUtils.setField(state, "screen", CatsTuiState.Screen.DETAIL);
        assertRenders(state);

        CatsTuiState failed = new CatsTuiState();
        failed.accept(new CatsExecutionEvent.SessionFailed(NOW, "first line\nsecond line"));
        assertRenders(failed);

        CatsTuiState cancelled = new CatsTuiState();
        cancelled.accept(new CatsExecutionEvent.SessionCancelled(NOW, null));
        assertRenders(cancelled);
    }

    @Test
    void shouldRenderPopulatedTablesFiltersSearchAndDetails() {
        CatsTuiState state = populatedState();

        assertRenders(state);
        state.handleKey(KeyEvent.ofChar('3'));
        assertRenders(state);

        state.handleKey(KeyEvent.ofChar('2'));
        assertRenders(state);
        for (char filter : new char[]{'e', 'w', 's', 'i', 'a'}) {
            state.handleKey(KeyEvent.ofChar(filter));
            assertRenders(state);
        }

        state.handleKey(KeyEvent.ofChar('/'));
        state.handleKey(KeyEvent.ofChar('z'));
        assertRenders(state);
        state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
        assertRenders(state);
        state.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE));

        state.handleKey(KeyEvent.ofChar('e'));
        state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
        assertRenders(state);
        state.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE));

        state.handleKey(KeyEvent.ofChar('4'));
        assertRenders(state);
        state.handleKey(KeyEvent.ofChar('5'));
        assertRenders(state);
        state.handleKey(KeyEvent.ofChar('6'));
        assertRenders(state);
    }

    @Test
    void shouldRenderCompletedSummariesForBothQualityGateOutcomes() {
        CatsTuiState failedGate = populatedState();
        failedGate.accept(new CatsExecutionEvent.SessionCompleted(NOW.plusSeconds(65),
                summary(false, "Errors are not allowed")));
        failedGate.handleKey(KeyEvent.ofChar('3'));
        assertRenders(failedGate);

        CatsTuiState passedGate = new CatsTuiState();
        passedGate.accept(new CatsExecutionEvent.SessionCompleted(NOW, summary(true, null)));
        passedGate.handleKey(KeyEvent.ofChar('3'));
        assertRenders(passedGate);
    }

    private static CatsTuiState populatedState() {
        CatsTuiState state = new CatsTuiState();
        state.accept(new CatsExecutionEvent.SessionStarted(NOW, "cats", "1", "today", "test"));
        state.accept(new CatsExecutionEvent.ConfigurationLoaded(NOW,
                new RunConfigurationSnapshot("1", "openapi.yml", "https://example.com", List.of("GET", "POST"),
                        2, 4, 3, 6)));
        state.accept(new CatsExecutionEvent.PathStarted(NOW, "/pets"));
        state.accept(new CatsExecutionEvent.FuzzerStarted(NOW, "SchemaFuzzer", "/pets", "POST"));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("1", "success", 200, 10, "/pets", "valid")));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("2", "warning", 422, 20, "/pets", "schema mismatch")));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("3", "error", 500, 50, "/orders", "server failed")));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("4", "skipped", 0, 0, "/orders", "not applicable")));
        state.accept(new CatsExecutionEvent.TestCompleted(NOW, test("5", "unclassified", 302, 15, "/other", "other")));
        state.accept(new CatsExecutionEvent.PathCompleted(NOW, "/pets"));
        return state;
    }

    private static TestResultSnapshot test(String id, String result, int code, long responseTime,
                                           String path, String reason) {
        List<TestResultSnapshot.HeaderSnapshot> headers = List.of(
                new TestResultSnapshot.HeaderSnapshot("Content-Type", "application/json"));
        return new TestResultSnapshot(id, "trace-" + id, "Scenario line one\nline two", "expected", result,
                reason, "detail:", "ignored", "SchemaFuzzer", path, path, "https://example.com" + path,
                "https://example.com", true,
                new TestResultSnapshot.RequestSnapshot("POST", path, "now", "{\"name\":\"cat\"}", headers),
                new TestResultSnapshot.ResponseSnapshot(code, "POST", responseTime, 16, 2, 1,
                        "application/json", "{\"result\":\"ok\"}", headers),
                "cats replay --test " + id);
    }

    private static RunSummarySnapshot summary(boolean qualityGatePassed, String description) {
        return new RunSummarySnapshot(8, 5, 1, 1, 1, 1, 3, 1, 2,
                Map.of(200, 1, 422, 1, 500, 1), Map.of("/orders", 2L),
                qualityGatePassed, description);
    }

    private static void assertRenders(CatsTuiState state) {
        Assertions.assertThat(CatsTuiView.render(state)).isNotNull();
    }
}
