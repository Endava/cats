package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Paths;

@QuarkusTest
class EmptyPathsLinterTest {
    private TestCaseListener testCaseListener;
    private EmptyPathsLinter emptyPathsLinterFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestReportsGenerator.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        emptyPathsLinterFuzzer = new EmptyPathsLinter(testCaseListener);
    }

    @Test
    void shouldReportError() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/empty-paths.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/users/{userId}")).build();
        emptyPathsLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Path is empty"), Mockito.eq("The current path must define at least one operation"));
    }

    @Test
    void shouldReportInfo() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/{id}")).build();
        emptyPathsLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Path is valid"));
    }

    @Test
    void shouldNotRunOnSecondAttempt() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/{id}")).build();
        emptyPathsLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Path is valid"));

        Mockito.reset(testCaseListener);
        emptyPathsLinterFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(emptyPathsLinterFuzzer).hasToString(emptyPathsLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(emptyPathsLinterFuzzer.description()).isEqualTo("verifies that the current path contains at least one operation");
    }
}
