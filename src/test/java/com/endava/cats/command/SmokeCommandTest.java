package com.endava.cats.command;

import com.endava.cats.args.CheckArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.tui.event.CatsExecutionEvent;
import com.endava.cats.tui.event.CatsExecutionEventPublisher;
import com.endava.cats.tui.model.TestResultSnapshot;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import java.time.Instant;
import java.util.List;

@QuarkusTest
class SmokeCommandTest {
    private SmokeCommand smokeCommand;
    private CatsCommand catsCommand;
    private FilterArguments filterArguments;

    @BeforeEach
    void setup() {
        smokeCommand = new SmokeCommand();
        catsCommand = Mockito.mock(CatsCommand.class);
        filterArguments = Mockito.mock(FilterArguments.class);
        CheckArguments checkArguments = new CheckArguments();
        Mockito.when(filterArguments.getCheckArguments()).thenReturn(checkArguments);

        ReflectionTestUtils.setField(catsCommand, "executionEventPublisher", new CatsExecutionEventPublisher());
        ReflectionTestUtils.setField(catsCommand, "processingArguments", new ProcessingArguments());
        ReflectionTestUtils.setField(catsCommand, "runtimeResourcePool", Mockito.mock(RuntimeResourcePool.class));
        Mockito.when(catsCommand.runtimeResourcePool.unresolvedOperations()).thenReturn(List.of());

        smokeCommand.catsCommand = catsCommand;
        ReflectionTestUtils.setField(smokeCommand, "filterArguments", filterArguments);
        ReflectionTestUtils.setField(smokeCommand, "reportingArguments", new ReportingArguments());
    }

    @Test
    void shouldRunHappyPathSmokeAndReportReadyVerdictOn2xx() {
        Mockito.doAnswer(_ -> {
            catsCommand.executionEventPublisher.publish(new CatsExecutionEvent.TestCompleted(
                    Instant.now(), snapshot("GET", "/pets", 200, 12, "success")));
            catsCommand.executionEventPublisher.publish(new CatsExecutionEvent.TestCompleted(
                    Instant.now(), snapshot("POST", "/pets", 400, 18, "warning")));
            return null;
        }).when(catsCommand).run();

        smokeCommand.run();

        Mockito.verify(filterArguments).customFilter("HappyPath");
        Mockito.verify(filterArguments).setSkippedHttpMethods(List.of(HttpMethod.DELETE));
        Assertions.assertThat(smokeCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
        Assertions.assertThat(smokeCommand.getLastReport()).isNotNull();
        Assertions.assertThat(smokeCommand.getLastReport().verdict()).isEqualTo("READY");
        Assertions.assertThat(smokeCommand.getLastReport().ok2xx()).isEqualTo(1);
        Assertions.assertThat(smokeCommand.getLastReport().validation4xx()).isEqualTo(1);
    }

    @Test
    void shouldFailSmokeCheckOn5xxOrTotalAuthFailureAndSupportJsonMode() {
        org.springframework.test.util.ReflectionTestUtils.setField(smokeCommand.reportingArguments, "jsonOutput", true);
        smokeCommand.includeDelete = true;
        Mockito.doAnswer(_ -> {
            catsCommand.executionEventPublisher.publish(new CatsExecutionEvent.TestCompleted(
                    Instant.now(), snapshot("DELETE", "/pets/{id}", 500, 35, "error")));
            return null;
        }).when(catsCommand).run();

        smokeCommand.run();

        Mockito.verify(filterArguments, Mockito.never()).setSkippedHttpMethods(Mockito.anyList());
        Assertions.assertThat(smokeCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
        Assertions.assertThat(smokeCommand.getLastReport().verdict()).isEqualTo("SERVER_CRASHES_DETECTED");
        Assertions.assertThat(smokeCommand.getLastReport().server5xx()).isEqualTo(1);
    }

    @Test
    void shouldDiagnoseAllStatusCodeCategories() {
        Assertions.assertThat(SmokeCommand.diagnoseStatusCode(201)).isEqualTo("2XX OK");
        Assertions.assertThat(SmokeCommand.diagnoseStatusCode(302)).isEqualTo("3XX Redirect");
        Assertions.assertThat(SmokeCommand.diagnoseStatusCode(401)).isEqualTo("Auth Required / Denied");
        Assertions.assertThat(SmokeCommand.diagnoseStatusCode(403)).isEqualTo("Auth Required / Denied");
        Assertions.assertThat(SmokeCommand.diagnoseStatusCode(404)).isEqualTo("Not Found (Route / ID)");
        Assertions.assertThat(SmokeCommand.diagnoseStatusCode(422)).isEqualTo("4XX Validation Rejected");
        Assertions.assertThat(SmokeCommand.diagnoseStatusCode(503)).isEqualTo("5XX Server Crash");
        Assertions.assertThat(SmokeCommand.diagnoseStatusCode(953)).isEqualTo("I/O / Connection Error");
    }

    private static TestResultSnapshot snapshot(String method, String path, int code, long timeMs, String result) {
        return new TestResultSnapshot("Test 1", "trace", "scenario", "2XX", result, "reason", "details", "",
                "HappyPath", path, path, path, "http://localhost", true,
                new TestResultSnapshot.RequestSnapshot(method, "http://localhost" + path, "now", "{}", List.of()),
                new TestResultSnapshot.ResponseSnapshot(code, method, timeMs, 2, 2, false, 1, 1,
                        "application/json", "{}", List.of()),
                "cats replay Test1");
    }
}
