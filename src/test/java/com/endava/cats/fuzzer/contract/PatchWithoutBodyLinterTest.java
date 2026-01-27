package com.endava.cats.fuzzer.contract;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
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
class PatchWithoutBodyLinterTest {
    private TestCaseListener testCaseListener;
    private PatchWithoutBodyLinter patchWithoutBodyLinterFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        patchWithoutBodyLinterFuzzer = new PatchWithoutBodyLinter(testCaseListener);
    }

    @Test
    void shouldReportWarn() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/operations-bad/{id}")).build();
        patchWithoutBodyLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.eq("PATCH without request body"), Mockito.eq("PATCH method should have a body"));
    }

    @Test
    void shouldReportInfo() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/operations/{id}")).reqSchema(new Schema()).build();
        patchWithoutBodyLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("PATCH method does have a body"));
    }

    @Test
    void shouldNotRunWhenNoPatch() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/pet-enum")).reqSchema(new Schema()).build();
        patchWithoutBodyLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(0)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(0)).reportResultError(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(0)).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldNotRunOnSecondAttempt() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/operations/{id}")).reqSchema(new Schema()).build();
        patchWithoutBodyLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("PATCH method does have a body"));

        Mockito.reset(testCaseListener);
        patchWithoutBodyLinterFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(patchWithoutBodyLinterFuzzer).hasToString(patchWithoutBodyLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(patchWithoutBodyLinterFuzzer.description()).isEqualTo("verifies that all PATCH operations define a request body schema (not null or empty)");
    }
}
