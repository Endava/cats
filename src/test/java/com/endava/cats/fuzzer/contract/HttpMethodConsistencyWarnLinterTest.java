package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.contract.util.HttpMethodConsistencyAnalyzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
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
class HttpMethodConsistencyWarnLinterTest {
    private TestCaseListener testCaseListener;
    private HttpMethodConsistencyWarnLinter httpMethodConsistencyWarnLinterFuzzer;
    @Inject
    HttpMethodConsistencyAnalyzer httpMethodConsistencyAnalyzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        httpMethodConsistencyWarnLinterFuzzer = new HttpMethodConsistencyWarnLinter(testCaseListener, httpMethodConsistencyAnalyzer);
    }

    @Test
    void shouldReportWarn() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/inconsistent-api.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/operations-bad/{id}")).build();
        httpMethodConsistencyWarnLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(Mockito.any(), Mockito.any(),
                Mockito.eq("Non-critical HTTP method consistency issues"),
                Mockito.eq("The following non-critical issues were found:\n- Group [/pets] missing DELETE on item path [/pets/{id}]; missing at least one of PUT or PATCH on item path [/pets/{id}]\n"));
    }

    @Test
    void shouldReportInfo() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/consistent-api.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/operations/{id}")).reqSchema(new Schema()).build();
        httpMethodConsistencyWarnLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(),
                Mockito.eq("All item paths have expected optional REST methods"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(httpMethodConsistencyWarnLinterFuzzer).hasToString(httpMethodConsistencyWarnLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(httpMethodConsistencyWarnLinterFuzzer.description()).isEqualTo("flags missing optional REST methods (DELETE, PUT/PATCH) on item paths");
    }
}
