package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@QuarkusTest
class CollectionPaginationLinterTest {
    private TestCaseListener testCaseListener;
    private CollectionPaginationLinter collectionPaginationLinterFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        collectionPaginationLinterFuzzer = new CollectionPaginationLinter(testCaseListener);
    }

    @Test
    void shouldReportWarn() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore31.yaml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pet/findByTags")).method(HttpMethod.GET).path("/pet/findByTags").build();
        collectionPaginationLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.eq("Missing pagination support on GET collection endpoints"),
                Mockito.contains("Operation is missing pagination query parameters"));
    }

    @Test
    void shouldSkipOnItemPath() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pets/operations/{id}")).path("/pets/operations/{id}")
                .method(HttpMethod.GET).reqSchema(new Schema()).build();
        collectionPaginationLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).skipTest(Mockito.any(), Mockito.eq("Skipping pagination check for non-GET operations"));
    }

    @Test
    void shouldSkipOnNonGetMethod() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore.yml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder().openApi(openAPI).pathItem(openAPI.getPaths().get("/pet/findByTags")).path("/pet/findByTags")
                .method(HttpMethod.POST).reqSchema(new Schema()).build();
        collectionPaginationLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).skipTest(Mockito.any(), Mockito.eq("Skipping pagination check for non-GET operations"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(collectionPaginationLinterFuzzer).hasToString(collectionPaginationLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(collectionPaginationLinterFuzzer.description()).isEqualTo("verifies that all GET operations on collection endpoints support pagination via query parameters like limit, offset, page, or cursor");
    }

    @Test
    void shouldReportInfoWhenPaginationIsSupported() throws Exception {
        OpenAPI openAPI = new OpenAPIParser().readContents(Files.readString(Paths.get("src/test/resources/petstore_with_pagination.yaml")), null, null).getOpenAPI();
        FuzzingData data = FuzzingData.builder()
                .openApi(openAPI)
                .pathItem(openAPI.getPaths().get("/pets"))
                .path("/pets")
                .method(HttpMethod.GET)
                .build();

        collectionPaginationLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All collection GET endpoints declare pagination support"));
    }

    @Test
    void shouldResolveRefPaginationParamsAndSkipSingletonEndpoints() {
        OpenAPI openAPI = new OpenAPI();
        Components components = new Components();
        Parameter pageSizeParam = new Parameter().in("query").name("page-size");
        components.addParameters("PageSize", pageSizeParam);
        openAPI.setComponents(components);

        PathItem collectionItem = new PathItem();
        Operation getCollection = new Operation();
        getCollection.setParameters(List.of(new Parameter().$ref("#/components/parameters/PageSize")));
        collectionItem.setGet(getCollection);

        FuzzingData collectionData = FuzzingData.builder()
                .openApi(openAPI)
                .pathItem(collectionItem)
                .path("/orders")
                .method(HttpMethod.GET)
                .build();
        collectionPaginationLinterFuzzer.fuzz(collectionData);
        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All collection GET endpoints declare pagination support"));

        FuzzingData singletonData = FuzzingData.builder()
                .openApi(openAPI)
                .pathItem(collectionItem)
                .path("/context/me")
                .method(HttpMethod.GET)
                .build();
        collectionPaginationLinterFuzzer.fuzz(singletonData);
        Mockito.verify(testCaseListener, Mockito.times(1))
                .skipTest(Mockito.any(), Mockito.eq("Skipping pagination check for non-GET operations"));
    }

    @Test
    void shouldHandleCyclicComposedSchemasWithoutStackOverflow() {
        OpenAPI openAPI = new OpenAPI();
        Schema<?> petSchema = new Schema<>().oneOf(List.of(new Schema<>().$ref("#/components/schemas/Dog")));
        Schema<?> dogSchema = new Schema<>().allOf(List.of(new Schema<>().$ref("#/components/schemas/Pet")));
        Components components = new Components().addSchemas("Pet", petSchema).addSchemas("Dog", dogSchema);
        openAPI.setComponents(components);

        Operation getCollection = new Operation().responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                .addApiResponse("200", new io.swagger.v3.oas.models.responses.ApiResponse()
                        .content(new io.swagger.v3.oas.models.media.Content()
                                .addMediaType("application/json", new io.swagger.v3.oas.models.media.MediaType()
                                        .schema(new Schema<>().$ref("#/components/schemas/Pet"))))));
        PathItem collectionItem = new PathItem().get(getCollection);

        FuzzingData data = FuzzingData.builder()
                .openApi(openAPI)
                .pathItem(collectionItem)
                .path("/pets")
                .method(HttpMethod.GET)
                .build();

        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> collectionPaginationLinterFuzzer.fuzz(data));
    }
}
