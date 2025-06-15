package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.fuzzer.contract.util.HttpMethodConsistencyAnalyzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Paths;

@QuarkusTest
class HttpMethodConsistencyErrorLinterTest {
    private TestCaseListener testCaseListener;
    private HttpMethodConsistencyErrorLinter httpMethodConsistencyErrorLinterFuzzer;
    @Inject
    HttpMethodConsistencyAnalyzer httpMethodConsistencyAnalyzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestReportsGenerator.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        httpMethodConsistencyErrorLinterFuzzer = new HttpMethodConsistencyErrorLinter(testCaseListener, httpMethodConsistencyAnalyzer);
    }

    @Test
    void shouldReportError() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/inconsistent-api.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/operations-bad/{id}")).build();
        httpMethodConsistencyErrorLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(),
                Mockito.eq("Critical HTTP method consistency issues"),
                Mockito.eq("The following critical issues were found:\n- Group [/pets] missing POST on collection path [/pets]\n"));
    }

    @Test
    void shouldReportInfo() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/consistent-api.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/operations/{id}")).reqSchema(new Schema()).build();
        httpMethodConsistencyErrorLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(),
                Mockito.eq("All resource groups have required GET on item paths and POST on collection paths"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(httpMethodConsistencyErrorLinterFuzzer).hasToString(httpMethodConsistencyErrorLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(httpMethodConsistencyErrorLinterFuzzer.description()).isEqualTo("flags missing critical HTTP methods: GET on item paths and POST on collection paths");
    }
}
