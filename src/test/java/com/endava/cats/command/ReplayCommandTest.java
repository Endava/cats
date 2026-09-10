package com.endava.cats.command;

import com.endava.cats.args.AuthArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
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
        replayCommand.authArguments = Mockito.mock(AuthArguments.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        if (tempDir == null) {
            tempDir = Files.createTempDirectory("replay-test-temp");
        }
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
                .singleElement().extracting(header -> header.getValue()).isEqualTo("OVERRIDDEN");
        Assertions.assertThat(request.getValue().getHeaders())
                .anySatisfy(header -> {
                    Assertions.assertThat(header.getKey()).isEqualTo("X-New");
                    Assertions.assertThat(header.getValue()).isEqualTo("added");
                });
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
}
