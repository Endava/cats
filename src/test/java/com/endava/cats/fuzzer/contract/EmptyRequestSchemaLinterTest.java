package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Paths;

@QuarkusTest
class EmptyRequestSchemaLinterTest {

    private TestCaseListener testCaseListener;
    private EmptyRequestSchemaLinter emptyRequestSchemaLinterFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestReportsGenerator.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        emptyRequestSchemaLinterFuzzer = new EmptyRequestSchemaLinter(testCaseListener);
    }

    @Test
    void shouldReportWarn() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/inconsistent-api-2.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).method(HttpMethod.PUT).pathItem(openAPI.getPaths().get("/users/{userId}")).build();
        emptyRequestSchemaLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.eq("Empty request schemas found"), Mockito.eq("Request schema for media type application/json is an empty object"));
    }

    @Test
    void shouldReportInfo() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/consistent-api.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).method(HttpMethod.POST).pathItem(openAPI.getPaths().get("/pets/states")).reqSchema(new Schema()).build();
        emptyRequestSchemaLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All request schemas define properties, $ref, or structural composition"));
    }

    @Test
    void shouldNotRunOnSecondAttempt() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/consistent-api.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/states")).method(HttpMethod.POST).reqSchema(new Schema()).build();
        emptyRequestSchemaLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All request schemas define properties, $ref, or structural composition"));

        Mockito.reset(testCaseListener);
        emptyRequestSchemaLinterFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(emptyRequestSchemaLinterFuzzer).hasToString(emptyRequestSchemaLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(emptyRequestSchemaLinterFuzzer.description()).isEqualTo("detects request schemas that define neither properties, $ref, nor structural composition (oneOf, anyOf, allOf)");
    }
}
