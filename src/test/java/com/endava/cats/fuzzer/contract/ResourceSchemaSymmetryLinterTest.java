package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.contract.util.HttpMethodConsistencyAnalyzer;
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
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

@QuarkusTest
class ResourceSchemaSymmetryLinterTest {
    private TestCaseListener testCaseListener;
    private ResourceSchemaSymmetryLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new ResourceSchemaSymmetryLinter(testCaseListener, new HttpMethodConsistencyAnalyzer());
    }

    @Test
    void shouldReportInfoWhenRequestAndResponseSchemasAreSymmetric() {
        Schema<?> createOrder = new Schema<>().type("object").properties(Map.of(
                "amount", new Schema<>().type("number").format("double")
        ));
        Schema<?> orderResponse = new Schema<>().type("object").properties(Map.of(
                "id", new Schema<>().type("string").format("uuid"),
                "amount", new Schema<>().type("number").format("double")
        ));

        OpenAPI openAPI = buildOrdersApi(createOrder, orderResponse);
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("All resource groups maintain symmetric property types and formats between requests and responses")
        );
    }

    @Test
    void shouldNotConfuseCollectionPaginationEnvelopeMetadataWithResourceProperties() {
        Schema<?> createShirt = new Schema<>().type("object").properties(Map.of(
                "size", new Schema<>().type("string")
        ));
        Schema<?> shirtResponse = new Schema<>().type("object").properties(Map.of(
                "id", new Schema<>().type("string"),
                "size", new Schema<>().type("string")
        ));
        Schema<?> paginatedEnvelope = new Schema<>().type("object").properties(Map.of(
                "items", new Schema<>().type("array").items(new Schema<>().$ref("#/components/schemas/ShirtResponse")),
                "size", new Schema<>().type("integer")
        ));

        Operation postOp = new Operation().requestBody(new RequestBody().content(
                new Content().addMediaType("application/json", new MediaType().schema(createShirt))));
        Operation collectionGetOp = new Operation().responses(new ApiResponses().addApiResponse("200",
                new ApiResponse().content(new Content().addMediaType("application/json", new MediaType().schema(paginatedEnvelope)))));
        Operation itemGetOp = new Operation().responses(new ApiResponses().addApiResponse("200",
                new ApiResponse().content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ShirtResponse"))))));

        OpenAPI openAPI = new OpenAPI()
                .components(new Components().addSchemas("ShirtResponse", shirtResponse))
                .paths(new Paths()
                        .addPathItem("/shirts", new PathItem().post(postOp).get(collectionGetOp))
                        .addPathItem("/shirts/{id}", new PathItem().get(itemGetOp)));

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("All resource groups maintain symmetric property types and formats between requests and responses")
        );
    }

    @Test
    void shouldReportWarnWhenPropertyTypesDivergeOrInlineRequestRequiresReadOnly() {
        Schema<?> createOrder = new Schema<>().type("object")
                .properties(Map.of(
                        "id", new Schema<>().type("string").readOnly(true),
                        "amount", new Schema<>().type("number")
                ))
                .required(List.of("id", "amount"));
        Schema<?> orderResponse = new Schema<>().type("object").properties(Map.of(
                "id", new Schema<>().type("string"),
                "amount", new Schema<>().type("string")
        ));

        Operation postOp = new Operation().requestBody(new RequestBody().content(
                new Content().addMediaType("application/json", new MediaType().schema(createOrder))));
        Operation getOp = new Operation().responses(new ApiResponses().addApiResponse("200",
                new ApiResponse().content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/OrderResponse"))))));

        OpenAPI openAPI = new OpenAPI()
                .components(new Components().addSchemas("OrderResponse", orderResponse))
                .paths(new Paths()
                        .addPathItem("/orders", new PathItem().post(postOp))
                        .addPathItem("/orders/{id}", new PathItem().get(getOp)));

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Resource schema symmetry issues detected"),
                Mockito.argThat((String msg) -> msg.contains("Path '/orders' inline request schema marks readOnly property 'id' as required")
                        && msg.contains("Group [/orders] property 'amount' has mismatched type/format between write request (number) and GET response (string)"))
        );
    }

    private static OpenAPI buildOrdersApi(Schema<?> createOrder, Schema<?> orderResponse) {
        Operation postOp = new Operation().requestBody(new RequestBody().content(
                new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/CreateOrder")))));
        Operation getOp = new Operation().responses(new ApiResponses().addApiResponse("200",
                new ApiResponse().content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/OrderResponse"))))));

        return new OpenAPI()
                .components(new Components()
                        .addSchemas("CreateOrder", createOrder)
                        .addSchemas("OrderResponse", orderResponse))
                .paths(new Paths()
                        .addPathItem("/orders", new PathItem().post(postOp))
                        .addPathItem("/orders/{id}", new PathItem().get(getOp)));
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("ResourceSchemaSymmetryLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("verifies that properties shared between write request schemas (POST/PUT/PATCH) and GET response schemas within a resource group have consistent types and formats");
    }

    @Test
    void shouldHandleCyclicComposedSchemasWithoutStackOverflow() {
        Schema<?> petSchema = new Schema<>().oneOf(List.of(new Schema<>().$ref("#/components/schemas/Dog")));
        Schema<?> dogSchema = new Schema<>().allOf(List.of(new Schema<>().$ref("#/components/schemas/Pet")));
        OpenAPI openAPI = buildOrdersApi(petSchema, dogSchema);
        openAPI.getComponents().addSchemas("Pet", petSchema).addSchemas("Dog", dogSchema);

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        Assertions.assertThatNoException().isThrownBy(() -> linter.fuzz(data));
    }
}
