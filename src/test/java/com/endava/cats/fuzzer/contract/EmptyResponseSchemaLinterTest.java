package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Paths;

@QuarkusTest
class EmptyResponseSchemaLinterTest {

    private TestCaseListener testCaseListener;
    private EmptyResponseSchemaLinter emptyResponseSchemaLinterFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        emptyResponseSchemaLinterFuzzer = new EmptyResponseSchemaLinter(testCaseListener);
    }

    @Test
    void shouldReportWarnWhenNoContent() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/inconsistent-api-2.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).method(HttpMethod.PUT).pathItem(openAPI.getPaths().get("/users/{userId}")).build();
        emptyResponseSchemaLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.eq("Empty response schemas found"), Mockito.eq("Response 200 does not define any content"));
    }

    @Test
    void shouldReportWarnWhenWhenEmptyObject() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/inconsistent-api-2.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).method(HttpMethod.POST).pathItem(openAPI.getPaths().get("/users/{userId}")).build();
        emptyResponseSchemaLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.eq("Empty response schemas found"), Mockito.eq("Response schema on response 200 is an empty object"));
    }

    @Test
    void shouldReportInfo() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/consistent-api.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).method(HttpMethod.POST).pathItem(openAPI.getPaths().get("/pets/states")).reqSchema(new Schema()).build();
        emptyResponseSchemaLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All response schemas define properties, $ref, or structural composition"));
    }

    @Test
    void shouldNotRunOnSecondAttempt() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/consistent-api.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/states")).method(HttpMethod.POST).reqSchema(new Schema()).build();
        emptyResponseSchemaLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All response schemas define properties, $ref, or structural composition"));

        Mockito.reset(testCaseListener);
        emptyResponseSchemaLinterFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(emptyResponseSchemaLinterFuzzer).hasToString(emptyResponseSchemaLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(emptyResponseSchemaLinterFuzzer.description()).isEqualTo("detects response schemas that define neither properties, $ref, nor structural composition (oneOf, anyOf, allOf)");
    }

    @Test
    void shouldReportInfoWhenOperationHasNoResponses() throws Exception {
        OpenAPI openAPI = new OpenAPIParser()
                .readContents(Files.readString(Paths.get("src/test/resources/consistent-api.yml")), null, null)
                .getOpenAPI();

        openAPI.getPaths().get("/pets/states").getPost().setResponses(null);

        FuzzingData data = FuzzingData.builder()
                .openApi(openAPI)
                .method(HttpMethod.POST)
                .pathItem(openAPI.getPaths().get("/pets/states"))
                .build();

        emptyResponseSchemaLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResultInfo(Mockito.any(), Mockito.any(),
                        Mockito.eq("All response schemas define properties, $ref, or structural composition"));
    }

    @Test
    void shouldReportInfoWhenOnlyIgnoredContentTypesPresent() throws Exception {
        OpenAPI openAPI = new OpenAPIParser()
                .readContents(Files.readString(Paths.get("src/test/resources/consistent-api.yml")), null, null)
                .getOpenAPI();

        ApiResponse api200 = openAPI.getPaths().get("/pets/states").getPost().getResponses().get("200");
        api200.setContent(new io.swagger.v3.oas.models.media.Content()
                .addMediaType("application/pdf", new io.swagger.v3.oas.models.media.MediaType())); // application/pdf is ignored

        FuzzingData data = FuzzingData.builder()
                .openApi(openAPI)
                .method(HttpMethod.POST)
                .pathItem(openAPI.getPaths().get("/pets/states"))
                .build();

        emptyResponseSchemaLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResultInfo(Mockito.any(), Mockito.any(),
                        Mockito.eq("All response schemas define properties, $ref, or structural composition"));
    }
}
