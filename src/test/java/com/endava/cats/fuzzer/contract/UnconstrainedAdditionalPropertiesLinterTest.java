package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class UnconstrainedAdditionalPropertiesLinterTest {
    private TestCaseListener testCaseListener;
    private UnconstrainedAdditionalPropertiesLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new UnconstrainedAdditionalPropertiesLinter(testCaseListener);
    }

    @Test
    void shouldReportInfoWhenAdditionalPropertiesIsFalseOrTyped() {
        Schema<?> typedMap = new Schema<>().type("object").additionalProperties(new Schema<>().type("string"));
        Operation postOp = new Operation().requestBody(new RequestBody().content(
                new Content().addMediaType("application/json", new MediaType().schema(typedMap))));
        PathItem pathItem = new PathItem().post(postOp);

        FuzzingData data = FuzzingData.builder()
                .openApi(new OpenAPI())
                .path("/books")
                .method(HttpMethod.POST)
                .pathItem(pathItem)
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("No unconstrained additionalProperties found in request body schemas")
        );
    }

    @Test
    void shouldReportWarnWhenRequestSchemaAllowsUnconstrainedAdditionalProperties() {
        Schema<?> openSchema = new Schema<>().type("object").additionalProperties(true);
        Operation postOp = new Operation().requestBody(new RequestBody().content(
                new Content().addMediaType("application/json", new MediaType().schema(openSchema))));
        PathItem pathItem = new PathItem().post(postOp);

        FuzzingData data = FuzzingData.builder()
                .openApi(new OpenAPI())
                .path("/books")
                .method(HttpMethod.POST)
                .pathItem(pathItem)
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("Unconstrained additionalProperties in request schema"),
                Mockito.contains("Request schema 'application/json' allows unconstrained additionalProperties")
        );
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("UnconstrainedAdditionalPropertiesLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("flags request body object schemas that explicitly allow unconstrained additionalProperties (true or an untyped free-form schema)");
    }
}
