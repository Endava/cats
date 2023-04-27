package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.CheckArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.factory.FuzzingDataFactory;
import com.endava.cats.fuzzer.contract.PathTagsContractInfoFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.http.HappyPathFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import jakarta.inject.Inject;
import java.util.List;

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
        Mockito.verify(testCaseListener, Mockito.times(0)).afterFuzz();
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
        Mockito.verify(testCaseListener, Mockito.times(0)).afterFuzz();
    }

    @ParameterizedTest
    @CsvSource({"/pets/*,1", "*-rec,2", "*types*,4"})
    void shouldRunPathsWhenEndingWithWildcard(String path, int invocations) {
        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/petstore.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");
        Mockito.when(filterArguments.getFirstPhaseFuzzersForPath()).thenReturn(List.of("HappyPathFuzzer"));
        Mockito.when(filterArguments.getPaths()).thenReturn(List.of(path));
        Mockito.when(filterArguments.getAllRegisteredFuzzers()).thenReturn(List.of(new HappyPathFuzzer(Mockito.mock(SimpleExecutor.class))));

        CatsCommand spyMain = Mockito.spy(catsMain);
        spyMain.run();

        Mockito.verify(testCaseListener, Mockito.times(invocations)).afterFuzz();
        Mockito.verify(testCaseListener, Mockito.times(invocations)).beforeFuzz(HappyPathFuzzer.class);

        ReflectionTestUtils.setField(apiArguments, "contract", "empty");
        ReflectionTestUtils.setField(apiArguments, "server", "empty");
    }

    @Test
    void givenContractAndServerParameter_whenStartingCats_thenParametersAreProcessedSuccessfully() throws Exception {
        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/petstore.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");
        ReflectionTestUtils.setField(reportingArguments, "logData", List.of("org.apache.wire:debug", "com.endava.cats:warn", "error"));
        ReflectionTestUtils.setField(reportingArguments, "skipLogs", List.of("complete", "notSkip"));

        ReflectionTestUtils.setField(reportingArguments, "debug", true);
        Mockito.when(filterArguments.getFirstPhaseFuzzersForPath()).thenReturn(List.of("PathTagsContractInfoFuzzer"));
        Mockito.when(filterArguments.getSuppliedFuzzers()).thenReturn(List.of("FunctionalFuzzer"));
        Mockito.when(filterArguments.getAllRegisteredFuzzers()).thenReturn(List.of(new PathTagsContractInfoFuzzer(testCaseListener)));
        Mockito.when(executionStatisticsListener.areManyIoErrors()).thenReturn(true);
        Mockito.when(executionStatisticsListener.getIoErrors()).thenReturn(10);

        CatsCommand spyMain = Mockito.spy(catsMain);
        spyMain.run();
        Mockito.verify(spyMain).createOpenAPI();
        Mockito.verify(spyMain).startFuzzing(Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(1)).startSession();
        Mockito.verify(testCaseListener, Mockito.times(1)).endSession();
        Mockito.verify(testCaseListener, Mockito.times(8)).afterFuzz();
        Mockito.verify(testCaseListener, Mockito.times(8)).beforeFuzz(PathTagsContractInfoFuzzer.class);

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
        CatsCommand spyMain = Mockito.spy(catsMain);
        Mockito.when(filterArguments.getFirstPhaseFuzzersForPath()).thenReturn(List.of("PathTagsContractInfoFuzzer"));
        Mockito.when(filterArguments.getAllRegisteredFuzzers()).thenReturn(List.of(new PathTagsContractInfoFuzzer(testCaseListener)));
        Mockito.when(executionStatisticsListener.areManyAuthErrors()).thenReturn(true);
        Mockito.when(executionStatisticsListener.getAuthErrors()).thenReturn(10);

        spyMain.run();
        Mockito.verify(spyMain).createOpenAPI();
        Mockito.verify(spyMain).startFuzzing(Mockito.any());
        Mockito.verify(fuzzingDataFactory).fromPathItem(Mockito.eq("/pet"), Mockito.any(), Mockito.any());
        Mockito.verify(fuzzingDataFactory, Mockito.times(0)).fromPathItem(Mockito.eq("/petss"), Mockito.any(), Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(20)).afterFuzz();
        Mockito.verify(testCaseListener, Mockito.times(20)).beforeFuzz(PathTagsContractInfoFuzzer.class);

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
