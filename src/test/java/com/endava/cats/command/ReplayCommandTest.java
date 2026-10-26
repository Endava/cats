package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.exception.CatsException;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument;
import com.endava.cats.fuzzer.special.FunctionalFlowParser;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
class ReplayCommandTest {

    @InjectMock
    private ServiceCaller serviceCaller;
    private ReplayCommand replayCommand;

    @InjectSpy
    private TestCaseListener testCaseListener;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        replayCommand = new ReplayCommand(serviceCaller, testCaseListener);
        ReflectionTestUtils.setField(replayCommand, "apiArguments", Mockito.mock(ApiArguments.class));
        ReflectionTestUtils.setField(replayCommand, "authArguments", Mockito.mock(AuthArguments.class));
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        if (tempDir == null) {
            tempDir = Files.createTempDirectory("replay-test-temp");
        }
    }

    @Test
    void shouldResolveReplayValuesFromEnvironmentFileContext() {
        Mockito.when(replayCommand.authArguments.getDynamicVariablesContext()).thenReturn(Map.of("Authorization", "Bearer from-file"));

        String resolved = ReflectionTestUtils.invokeMethod(replayCommand, "resolveReplayValue", "$$Authorization", "header Authorization");

        Assertions.assertThat(resolved).isEqualTo("Bearer from-file");
    }

    @Test
    void shouldResolveReplayUrlValuesFromEnvironmentFileContext() {
        Mockito.when(replayCommand.authArguments.getDynamicVariablesContext()).thenReturn(Map.of("ACCESS_TOKEN", "from-file"));

        String resolved = ReflectionTestUtils.invokeMethod(replayCommand, "resolveReplayUrl",
                "https://service.test/items?access_token=$$ACCESS_TOKEN");

        Assertions.assertThat(resolved).isEqualTo("https://service.test/items?access_token=from-file");
    }

    @Test
    void shouldFailReplayWhenEnvironmentVariableIsMissing() {
        Mockito.when(replayCommand.authArguments.getDynamicVariablesContext()).thenReturn(Map.of());

        Assertions.assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(replayCommand, "resolveReplayValue",
                        "$$CATS_MISSING_REPLAY_TOKEN", "header Authorization"))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("CATS_MISSING_REPLAY_TOKEN", "--envFile", "-H");
    }

    @Test
    void shouldNotExecuteIfTestCasesNotSupplied() {
        replayCommand.tests = new String[]{};

        replayCommand.run();
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldExecuteIfTestCasesSupplied(boolean debug) throws Exception {
        replayCommand.tests = new String[]{"src/test/resources/Test12.json"};
        ReflectionTestUtils.setField(replayCommand, "debug", debug);
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("");
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenReturn(response);
        replayCommand.run();
        Mockito.verify(serviceCaller, Mockito.times(1)).callService(Mockito.any(), Mockito.eq(Collections.emptySet()));
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldApplyReplayHeaderOverridesToTheRequest() throws Exception {
        replayCommand.tests = new String[]{"src/test/resources/Test12.json"};
        replayCommand.headersMap = new HashMap<>(Map.of("X-Vault-Token", "OVERRIDDEN", "X-New", "added"));
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("");
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenReturn(response);

        replayCommand.run();

        ArgumentCaptor<CatsRequest> request = ArgumentCaptor.forClass(CatsRequest.class);
        Mockito.verify(serviceCaller).callService(request.capture(), Mockito.eq(Collections.emptySet()));
        Assertions.assertThat(request.getValue().getHeaders())
                .filteredOn(header -> "X-Vault-Token".equals(header.getKey()))
                .singleElement().extracting(KeyValuePair::getValue).isEqualTo("OVERRIDDEN");
        Assertions.assertThat(request.getValue().getHeaders())
                .anySatisfy(header -> {
                    Assertions.assertThat(header.getKey()).isEqualTo("X-New");
                    Assertions.assertThat(header.getValue()).isEqualTo("added");
                });
    }

    @Test
    void shouldResolveEmbeddedVariablesInReplayHeaderOverrides() {
        Mockito.when(replayCommand.authArguments.getDynamicVariablesContext()).thenReturn(Map.of("token", "token-123"));
        replayCommand.headersMap = new HashMap<>(Map.of("Authorization", "Bearer ${token}"));
        CatsTestCase testCase = new CatsTestCase();

        ReflectionTestUtils.invokeMethod(replayCommand, "loadHeadersIfSupplied", testCase);

        Assertions.assertThat(testCase.getRequest().getHeaders())
                .singleElement()
                .extracting(KeyValuePair::getValue)
                .isEqualTo("Bearer token-123");
    }

    @Test
    void shouldWriteTestsInOutputFolder() throws Exception {
        replayCommand.tests = new String[]{"src/test/resources/Test12.json"};
        ReflectionTestUtils.setField(replayCommand, "outputReportFolder", "replay-report");
        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("");
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenReturn(response);
        replayCommand.run();
        Mockito.verify(serviceCaller, Mockito.times(1)).callService(Mockito.any(), Mockito.eq(Collections.emptySet()));
        Mockito.verify(testCaseListener).writeHelperFiles();
        Mockito.verify(testCaseListener).writeIndividualTestCase(Mockito.any());
    }

    @Test
    void shouldReturnSoftwareExitCodeWhenOutputReportInitializationFails() throws Exception {
        replayCommand.tests = new String[]{"src/test/resources/Test12.json"};
        ReflectionTestUtils.setField(replayCommand, "outputReportFolder", "replay-report");
        Mockito.doThrow(new IOException("output is read-only"))
                .when(testCaseListener).initReportingPath("replay-report");

        replayCommand.run();

        Assertions.assertThat(replayCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldThrowExceptionWhenTestCasesInvalid() {
        replayCommand.tests = new String[]{"Test1212121212121"};
        replayCommand.run();

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldReturnCustomIOResponseWhenIOException() throws Exception {
        replayCommand.tests = new String[]{"src/test/resources/Test12.json"};
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenThrow(new IOException("connection refused"));
        ReplayCommand spyReplay = Mockito.spy(replayCommand);
        spyReplay.run();
        Mockito.verify(spyReplay, Mockito.times(1)).showResponseCodesDifferences(Mockito.any(), Mockito.argThat(catsResponse -> catsResponse.getResponseCode() == 953));
    }

    @Test
    void shouldSelectTestsAndCollectInitialStatsInOneSummaryParse() throws Exception {
        Path reportFolder = tempDir.resolve("cats-report-stats");
        Files.createDirectories(reportFolder);
        Files.writeString(reportFolder.resolve("cats-summary-report.json"), """
                {"testCases": [
                  {"id": "Test 1", "result": "error"},
                  {"id": "Test 2", "result": "warn"},
                  {"id": "Test 3", "result": "success"}
                ]}
                """);
        ReflectionTestUtils.setField(replayCommand, "reportFolder", reportFolder.toString());
        ReflectionTestUtils.setField(replayCommand, "errors", true);
        ReplayCommand.ReplayStats stats = new ReplayCommand.ReplayStats();

        List<String> selected = ReflectionTestUtils.invokeMethod(replayCommand, "loadTestIdsAndInitialStats", stats);

        Assertions.assertThat(selected).containsExactly(reportFolder.resolve("Test1.json").toString());
        Assertions.assertThat(stats.initialErrors).isEqualTo(1);
        Assertions.assertThat(stats.initialWarnings).isEqualTo(1);
    }

    @Test
    void shouldRetryErrorsFromSummaryReport() throws Exception {
        Path reportFolder = tempDir.resolve("cats-report-retry");
        Files.createDirectories(reportFolder);
        String summaryJson = "{\"testCases\": [{\"id\": \"Test 12\", \"result\": \"error\"}]}";
        Files.writeString(reportFolder.resolve("cats-summary-report.json"), summaryJson);
        String testContent = Files.readString(Path.of("src/test/resources/Test12.json"));
        Files.writeString(reportFolder.resolve("Test12.json"), testContent);

        ReflectionTestUtils.setField(replayCommand, "reportFolder", reportFolder.toString());
        ReflectionTestUtils.setField(replayCommand, "errors", true);

        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("");
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenReturn(response);

        replayCommand.run();
        Mockito.verify(serviceCaller, Mockito.times(1)).callService(Mockito.any(), Mockito.eq(Collections.emptySet()));
    }

    @Test
    void shouldRetryWarningsFromSummaryReport() throws Exception {
        Path reportFolder = tempDir.resolve("cats-report-warnings");
        Files.createDirectories(reportFolder);
        String summaryJson = "{\"testCases\": [{\"id\": \"Test 12\", \"result\": \"warn\"}]}";
        Files.writeString(reportFolder.resolve("cats-summary-report.json"), summaryJson);
        String testContent = Files.readString(Path.of("src/test/resources/Test12.json"));
        Files.writeString(reportFolder.resolve("Test12.json"), testContent);

        ReflectionTestUtils.setField(replayCommand, "reportFolder", reportFolder.toString());
        ReflectionTestUtils.setField(replayCommand, "warnings", true);

        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("");
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenReturn(response);

        replayCommand.run();
        Mockito.verify(serviceCaller, Mockito.times(1)).callService(Mockito.any(), Mockito.eq(Collections.emptySet()));
    }

    @Test
    void shouldNotRetrySuccessTests() throws Exception {
        Path reportFolder = tempDir.resolve("cats-report-success");
        Files.createDirectories(reportFolder);
        String summaryJson = "{\"testCases\": [{\"id\": \"Test 1\", \"result\": \"success\"}]}";
        Files.writeString(reportFolder.resolve("cats-summary-report.json"), summaryJson);

        ReflectionTestUtils.setField(replayCommand, "reportFolder", reportFolder.toString());
        ReflectionTestUtils.setField(replayCommand, "errors", true);

        replayCommand.run();
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldHandleMissingSummaryReport() {
        ReflectionTestUtils.setField(replayCommand, "reportFolder", "/non/existent/path");
        ReflectionTestUtils.setField(replayCommand, "errors", true);

        replayCommand.run();
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldCombineRetryAndExplicitTests() throws Exception {
        Path reportFolder = tempDir.resolve("cats-report-combined");
        Files.createDirectories(reportFolder);
        String summaryJson = "{\"testCases\": [{\"id\": \"Test 12\", \"result\": \"error\"}]}";
        Files.writeString(reportFolder.resolve("cats-summary-report.json"), summaryJson);
        String testContent = Files.readString(Path.of("src/test/resources/Test12.json"));
        Files.writeString(reportFolder.resolve("Test12.json"), testContent);

        ReflectionTestUtils.setField(replayCommand, "reportFolder", reportFolder.toString());
        ReflectionTestUtils.setField(replayCommand, "errors", true);
        replayCommand.tests = new String[]{"src/test/resources/Test12.json"};

        CatsResponse response = Mockito.mock(CatsResponse.class);
        Mockito.when(response.getBody()).thenReturn("");
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenReturn(response);

        replayCommand.run();
        Mockito.verify(serviceCaller, Mockito.times(2)).callService(Mockito.any(), Mockito.eq(Collections.emptySet()));
    }

    @Test
    void shouldPrintCurlCommandWithoutCallingServiceWhenCurlEnabled() {
        replayCommand.tests = new String[]{"src/test/resources/Test12.json"};
        replayCommand.headersMap = new HashMap<>(Map.of("X-Note", "it's a test"));
        ReflectionTestUtils.setField(replayCommand, "curl", true);

        replayCommand.run();

        Mockito.verifyNoInteractions(serviceCaller);
        CatsRequest postReq = CatsRequest.builder()
                .httpMethod("POST")
                .url("https://api.example.com/items")
                .headers(List.of(new KeyValuePair<>("Content-Type", "application/json"),
                        new KeyValuePair<>("X-Note", "it's a test")))
                .payload("{\"name\":\"item\"}")
                .build();
        String curlCmd = replayCommand.buildCurlCommand(postReq);
        Assertions.assertThat(curlCmd)
                .isEqualTo("curl -i -X POST -H 'Content-Type: application/json' -H 'X-Note: it'\\''s a test' -d '{\"name\":\"item\"}' 'https://api.example.com/items'");

        CatsRequest getReq = CatsRequest.builder()
                .httpMethod("GET")
                .url("https://api.example.com/items")
                .payload("{}")
                .build();
        Assertions.assertThat(replayCommand.buildCurlCommand(getReq))
                .isEqualTo("curl -i -X GET 'https://api.example.com/items'");
    }

    @Test
    void shouldReplayUpstreamProducersStatefullyAndExportFunctionalFlow(@TempDir Path dir) throws Exception {
        Path reportDir = dir.resolve("stateful-report");
        Files.createDirectories(reportDir);

        Files.writeString(reportDir.resolve("Test1.json"), """
                {
                  "testId": "Test 1",
                  "scenario": "Create order",
                  "result": "success",
                  "contractPath": "/orders",
                  "path": "/orders",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://localhost:8080/orders",
                    "payload": "{\\"item\\":\\"book\\"}",
                    "headers": []
                  },
                  "response": {
                    "responseCode": 201,
                    "body": "{\\"id\\":\\"ord_old_100\\"}"
                  }
                }
                """);

        Files.writeString(reportDir.resolve("Test2.json"), """
                {
                  "testId": "Test 2",
                  "scenario": "Update order quantity",
                  "result": "error",
                  "contractPath": "/orders/{orderId}",
                  "path": "/orders/ord_old_100",
                  "request": {
                    "httpMethod": "PUT",
                    "url": "http://localhost:8080/orders/ord_old_100",
                    "payload": "{\\"orderId\\":\\"ord_old_100\\",\\"qty\\":-1}",
                    "headers": []
                  },
                  "response": {
                    "responseCode": 500,
                    "body": "{\\"error\\":\\"crash\\"}"
                  },
                  "requestProvenance": {
                    "runtimeCorrelations": [{
                      "sourceMethod": "POST",
                      "sourcePath": "/orders",
                      "sourceLocation": "response.body.$.id",
                      "target": {"location": "PATH", "name": "orderId"},
                      "value": "ord_old_100"
                    }]
                  }
                }
                """);

        Files.writeString(reportDir.resolve("Test3.json"), """
                {
                  "testId": "Test 3",
                  "scenario": "Fallback parent collection inference",
                  "result": "error",
                  "contractPath": "/orders/{orderId}",
                  "path": "/orders/ord_old_100",
                  "request": {
                    "httpMethod": "GET",
                    "url": "http://localhost:8080/orders/ord_old_100",
                    "payload": "{}",
                    "headers": []
                  },
                  "response": {
                    "responseCode": 404,
                    "body": "{}"
                  }
                }
                """);

        // 1. Export as a runnable FunctionalFlow YAML
        Path exportedFlow = dir.resolve("reproducer-flow.yml");
        replayCommand.tests = new String[]{reportDir.resolve("Test2.json").toString(), reportDir.resolve("Test3.json").toString()};
        replayCommand.exportFlowFile = exportedFlow.toFile();
        replayCommand.run();

        Assertions.assertThat(exportedFlow).exists();
        FunctionalFlowDocument doc = FunctionalFlowParser.parse(exportedFlow.toFile());
        Assertions.assertThat(doc.steps()).hasSize(3);
        Assertions.assertThat(doc.steps().getFirst().save()).containsEntry("orderId", "id");
        Assertions.assertThat(doc.steps().get(1).rawTarget()).isEqualTo("/orders/${orderId}");
        Assertions.assertThat(doc.steps().get(1).expect().that()).containsExactly("status < 500");

        // 2. Live --stateful replay substitutes fresh ID from live POST response into PUT URL & payload
        replayCommand.exportFlowFile = null;
        replayCommand.stateful = true;
        ReflectionTestUtils.setField(replayCommand, "verbose", true);
        replayCommand.tests = new String[]{reportDir.resolve("Test2.json").toString()};

        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenAnswer(inv -> {
            CatsRequest req = inv.getArgument(0);
            if ("POST".equalsIgnoreCase(req.getHttpMethod())) {
                return CatsResponse.builder().responseCode(201).body("{\"id\":\"ord_fresh_999\"}").build();
            }
            return CatsResponse.builder().responseCode(400).body("{\"message\":\"validated\"}").build();
        });

        replayCommand.run();

        ArgumentCaptor<CatsRequest> captor = ArgumentCaptor.forClass(CatsRequest.class);
        Mockito.verify(serviceCaller, Mockito.times(2)).callService(captor.capture(), Mockito.eq(Collections.emptySet()));
        List<CatsRequest> calls = captor.getAllValues();
        Assertions.assertThat(calls.get(0).getHttpMethod()).isEqualTo("POST");
        Assertions.assertThat(calls.get(1).getUrl()).isEqualTo("http://localhost:8080/orders/ord_fresh_999");
        Assertions.assertThat(calls.get(1).getPayload()).contains("ord_fresh_999");
    }
}
