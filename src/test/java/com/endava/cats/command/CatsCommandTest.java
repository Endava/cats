package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.CheckArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.factory.FuzzingDataFactory;
import com.endava.cats.fuzzer.contract.PathTagsLinterFuzzer;
import com.endava.cats.fuzzer.http.CheckDeletedResourcesNotAvailableFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.VersionChecker;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Future;

@QuarkusTest
class CatsCommandTest {

    @Inject
    CatsCommand catsMain;
    @InjectSpy
    ExecutionStatisticsListener executionStatisticsListener;
    @Inject
    CheckArguments checkArguments;
    @Inject
    ReportingArguments reportingArguments;
    @Inject
    ApiArguments apiArguments;
    @InjectSpy
    FuzzingDataFactory fuzzingDataFactory;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private FilterArguments filterArguments;

    @BeforeEach
    void setup() {
        filterArguments = Mockito.mock(FilterArguments.class);
        ReflectionTestUtils.setField(catsMain, "filterArguments", filterArguments);
        Mockito.when(filterArguments.getHttpMethods()).thenReturn(HttpMethod.restMethods());
        ReflectionTestUtils.setField(reportingArguments, "verbosity", ReportingArguments.Verbosity.DETAILED);
        catsMain.initLogger();
    }

    @Test
    void shouldCheckForNewVersion() throws Exception {
        ReportingArguments repArgs = Mockito.mock(ReportingArguments.class);
        Mockito.when(repArgs.isCheckUpdate()).thenReturn(true);
        ReflectionTestUtils.setField(catsMain, "reportingArguments", repArgs);
        VersionChecker checker = Mockito.mock(VersionChecker.class);
        Mockito.when(checker.checkForNewVersion(Mockito.anyString())).thenReturn(VersionChecker.CheckResult.builder().newVersion(true).version("1.0.0").build());
        ReflectionTestUtils.setField(catsMain, "versionChecker", checker);

        Future<VersionChecker.CheckResult> resultFuture = catsMain.checkForNewVersion();
        catsMain.printVersion(resultFuture);

        VersionChecker.CheckResult result = resultFuture.get();
        Assertions.assertThat(result.isNewVersion()).isTrue();
        Assertions.assertThat(result.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void shouldNotCheckForNewVersion() throws Exception {
        ReportingArguments repArgs = Mockito.mock(ReportingArguments.class);
        Mockito.when(repArgs.isCheckUpdate()).thenReturn(false);
        ReflectionTestUtils.setField(catsMain, "reportingArguments", repArgs);
        VersionChecker checker = Mockito.mock(VersionChecker.class);
        Mockito.when(checker.checkForNewVersion(Mockito.anyString())).thenReturn(VersionChecker.CheckResult.builder().newVersion(true).version("1.0.0").build());
        ReflectionTestUtils.setField(catsMain, "versionChecker", checker);

        Future<VersionChecker.CheckResult> resultFuture = catsMain.checkForNewVersion();
        catsMain.printVersion(resultFuture);

        VersionChecker.CheckResult result = resultFuture.get();
        Assertions.assertThat(result.isNewVersion()).isFalse();
        Assertions.assertThat(result.getVersion()).isNotEqualTo("1.0.0");
    }

    @Test
    void shouldNotRunWhenNotRecognizedContentType() throws Exception {
        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/petstore-nonjson.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");


        CatsCommand spyMain = Mockito.spy(catsMain);
        spyMain.run();
        Mockito.verify(spyMain).createOpenAPI();
        Mockito.verify(spyMain).startFuzzing(Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(1)).startSession();
        Mockito.verify(testCaseListener, Mockito.times(1)).endSession();
        Mockito.verify(testCaseListener, Mockito.times(0)).afterFuzz(Mockito.any(), Mockito.any());
        ReflectionTestUtils.setField(apiArguments, "contract", "empty");
        ReflectionTestUtils.setField(apiArguments, "server", "empty");
    }

    @Test
    void shouldNotCallEndSessionWhenIOException() {
        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/not_existent.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");

        catsMain.run();
        Mockito.verify(testCaseListener, Mockito.times(1)).startSession();
        Mockito.verify(testCaseListener, Mockito.times(0)).endSession();
        Mockito.verify(testCaseListener, Mockito.times(0)).afterFuzz(Mockito.any(), Mockito.any());
    }

    @Test
    void givenContractAndServerParameter_whenStartingCats_thenParametersAreProcessedSuccessfully() throws Exception {
        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/petstore.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");
        ReflectionTestUtils.setField(reportingArguments, "logData", List.of("org.apache.wire:debug", "com.endava.cats:warn", "error"));
        ReflectionTestUtils.setField(reportingArguments, "skipLogs", List.of("complete", "notSkip"));

        ReflectionTestUtils.setField(reportingArguments, "debug", true);
        Mockito.when(filterArguments.getFirstPhaseFuzzersForPath()).thenReturn(List.of("PathTagsLinterFuzzer"));
        Mockito.when(filterArguments.getSuppliedFuzzers()).thenReturn(List.of("FunctionalFuzzer"));
        Mockito.when(filterArguments.isHttpMethodSupplied(Mockito.any())).thenReturn(true);
        Mockito.when(filterArguments.getFirstPhaseFuzzersAsFuzzers()).thenReturn(List.of(new PathTagsLinterFuzzer(testCaseListener)));
        Mockito.when(filterArguments.getSecondPhaseFuzzers()).thenReturn(List.of(Mockito.mock(CheckDeletedResourcesNotAvailableFuzzer.class)));
        Mockito.when(executionStatisticsListener.areManyIoErrors()).thenReturn(true);
        Mockito.when(executionStatisticsListener.getIoErrors()).thenReturn(10);
        Mockito.when(filterArguments.getPathsToRun(Mockito.any())).thenReturn(List.of("/pet-types", "/pet-types-rec", "/pets", "/pets-batch", "/pets/{id}"));
        CatsCommand spyMain = Mockito.spy(catsMain);
        spyMain.run();
        Mockito.verify(spyMain).createOpenAPI();
        Mockito.verify(spyMain).startFuzzing(Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(1)).startSession();
        Mockito.verify(testCaseListener, Mockito.times(1)).endSession();
        Mockito.verify(testCaseListener, Mockito.times(20)).afterFuzz(Mockito.any(), Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(10)).beforeFuzz(PathTagsLinterFuzzer.class);

        ReflectionTestUtils.setField(apiArguments, "contract", "empty");
        ReflectionTestUtils.setField(apiArguments, "server", "empty");
    }

    @Test
    void givenAnOpenApiContract_whenStartingCats_thenTheContractIsCorrectlyParsed() throws Exception {
        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/openapi.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");
        ReflectionTestUtils.setField(checkArguments, "includeEmojis", true);
        ReflectionTestUtils.setField(checkArguments, "includeControlChars", true);
        ReflectionTestUtils.setField(checkArguments, "includeWhitespaces", true);
        ReflectionTestUtils.setField(reportingArguments, "verbosity", ReportingArguments.Verbosity.SUMMARY);

        CatsCommand spyMain = Mockito.spy(catsMain);
        Mockito.when(filterArguments.getFirstPhaseFuzzersForPath()).thenReturn(List.of("PathTagsLinterFuzzer"));
        Mockito.when(filterArguments.isHttpMethodSupplied(Mockito.any())).thenReturn(true);
        Mockito.when(filterArguments.getFirstPhaseFuzzersAsFuzzers()).thenReturn(List.of(new PathTagsLinterFuzzer(testCaseListener)));
        Mockito.when(filterArguments.getSecondPhaseFuzzers()).thenReturn(List.of(new CheckDeletedResourcesNotAvailableFuzzer(null, Mockito.mock(CatsGlobalContext.class), null)));
        Mockito.when(filterArguments.getPathsToRun(Mockito.any())).thenReturn(
                List.of("/pet", "/pets", "/pet/findByStatus", "/pet/findByTags", "/pet/{petId}", "/pet/{petId}/uploadImage", "/store/inventory"));
        Mockito.when(executionStatisticsListener.areManyAuthErrors()).thenReturn(true);
        Mockito.when(executionStatisticsListener.getAuthErrors()).thenReturn(9);

        spyMain.run();
        Mockito.verify(spyMain).createOpenAPI();
        Mockito.verify(spyMain).startFuzzing(Mockito.any());
        Mockito.verify(fuzzingDataFactory).fromPathItem(Mockito.eq("/pet"), Mockito.any(), Mockito.any());
        Mockito.verify(fuzzingDataFactory, Mockito.times(0)).fromPathItem(Mockito.eq("/petss"), Mockito.any(), Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(11)).afterFuzz(Mockito.any(), Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(8)).beforeFuzz(PathTagsLinterFuzzer.class);
        Mockito.verify(testCaseListener, Mockito.times(3)).beforeFuzz(CheckDeletedResourcesNotAvailableFuzzer.class);

        ReflectionTestUtils.setField(apiArguments, "contract", "empty");
        ReflectionTestUtils.setField(apiArguments, "server", "empty");
    }

    @Test
    void shouldReturnErrorsExitCode() {
        Mockito.when(executionStatisticsListener.getErrors()).thenReturn(190);

        Assertions.assertThat(catsMain.getExitCode()).isEqualTo(190);
    }

    @Test
    void shouldThrowExceptionWhenNoContract() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ReflectionTestUtils.setField(apiArguments, "contract", null);
        ReflectionTestUtils.setField(catsMain, "spec", spec);

        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");
        Assertions.assertThatThrownBy(() -> catsMain.run()).isInstanceOf(CommandLine.ParameterException.class).hasMessage("Missing required option --contract=<contract>");
    }
}
