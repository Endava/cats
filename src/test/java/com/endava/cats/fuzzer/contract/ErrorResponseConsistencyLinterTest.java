package com.endava.cats.fuzzer.contract;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

@QuarkusTest
class ErrorResponseConsistencyLinterTest {
    private TestCaseListener testCaseListener;
    private ErrorResponseConsistencyLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new ErrorResponseConsistencyLinter(testCaseListener);
    }

    @Test
    void shouldReportInfoWhenAllErrorResponsesUseConsistentSchema() {
        Schema<?> errorSchema = new Schema<>().type("object").properties(Map.of(
                "code", new Schema<>().type("string"),
                "message", new Schema<>().type("string")
        ));
        OpenAPI openAPI = new OpenAPI()
                .components(new Components().addSchemas("Problem", errorSchema))
                .paths(new Paths()
                        .addPathItem("/users", new PathItem().get(operationWithError("400", "#/components/schemas/Problem")))
                        .addPathItem("/orders", new PathItem().post(operationWithError("500", "#/components/schemas/Problem"))));

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("All error responses use a consistent structured schema")
        );
    }

    @Test
    void shouldReportWarnWhenErrorResponsesArePrimitiveOrDivergent() {
        Schema<?> standardError = new Schema<>().type("object").properties(Map.of(
                "code", new Schema<>().type("string"),
                "message", new Schema<>().type("string")
        ));
        Schema<?> divergentError = new Schema<>().type("object").properties(Map.of(
                "faultId", new Schema<>().type("integer"),
                "reasonText", new Schema<>().type("string")
        ));
        Operation primitiveOp = new Operation().responses(new ApiResponses().addApiResponse("500",
                new ApiResponse().content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().type("string"))))));

        OpenAPI openAPI = new OpenAPI()
                .components(new Components()
                        .addSchemas("StandardError", standardError)
                        .addSchemas("DivergentError", divergentError))
                .paths(new Paths()
                        .addPathItem("/users", new PathItem()
                                .get(operationWithError("400", "#/components/schemas/StandardError"))
                                .post(operationWithError("400", "#/components/schemas/StandardError")))
                        .addPathItem("/legacy", new PathItem()
                                .get(operationWithError("400", "#/components/schemas/DivergentError"))
                                .post(primitiveOp)));

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Inconsistent or unstructured error response schemas found"),
                Mockito.argThat((String msg) -> msg.contains("POST /legacy (500) defines a primitive 'string' error schema")
                        && msg.contains("diverges from dominant error schema [code,message]"))
        );
    }

    private static Operation operationWithError(String status, String ref) {
        return new Operation().responses(new ApiResponses().addApiResponse(status,
                new ApiResponse().content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref(ref))))));
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("ErrorResponseConsistencyLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("verifies that 4xx and 5xx error responses across the contract share a consistent structured error schema");
    }
}
