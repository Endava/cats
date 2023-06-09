package com.endava.cats.factory;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.OpenApiUtils;
import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class FuzzingDataFactoryTest {
    @Inject
    CatsGlobalContext catsGlobalContext;
    @Inject
    ValidDataFormat validDataFormat;
    private FilesArguments filesArguments;
    private ProcessingArguments processingArguments;
    private FuzzingDataFactory fuzzingDataFactory;

    @BeforeEach
    void setup() {
        filesArguments = Mockito.mock(FilesArguments.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        Mockito.when(processingArguments.isUseExamples()).thenReturn(true);
        Mockito.when(processingArguments.getContentType()).thenReturn(List.of("application/json", "application/x-www-form-urlencoded"));
        fuzzingDataFactory = new FuzzingDataFactory(filesArguments, processingArguments, catsGlobalContext, validDataFormat);
    }

    @Test
    void shouldGenerateMultiplePayloadsWhenRootOneOfAndDiscriminatorAndAllOfAndMappings() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/variant", "src/test/resources/issue_69.yml");
        Assertions.assertThat(data).hasSize(1);

        List<String> responses = data.get(0).getResponses().get("200");
        Assertions.assertThat(responses).hasSize(2);
        Assertions.assertThat(responses.get(0)).doesNotContain("Variant").contains("option2");
        Assertions.assertThat(responses.get(1)).doesNotContain("Variant").contains("option1");
    }

    @Test
    void givenAContract_whenParsingThePathItemDetailsForPost_thenCorrectFuzzingDataAreBeingReturned() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/petstore.yml");

        Assertions.assertThat(data).hasSize(3);
        Assertions.assertThat(data.get(0).getMethod()).isEqualByComparingTo(HttpMethod.POST);
        Assertions.assertThat(data.get(1).getMethod()).isEqualByComparingTo(HttpMethod.POST);
        Assertions.assertThat(data.get(2).getMethod()).isEqualByComparingTo(HttpMethod.GET);

        Assertions.assertThat(data.get(0).getPayload()).doesNotContain("ONE_OF", "ANY_OF");
        Assertions.assertThat(data.get(1).getPayload()).doesNotContain("ONE_OF", "ANY_OF");
    }

    @Test
    void shouldLoadExamples() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/petstore.yml");
        Assertions.assertThat(data.get(0).getExamples()).hasSize(2);
        Assertions.assertThat(data.get(0).getExamples())
                .anyMatch(example -> example.contains("dog-example"))
                .anyMatch(example -> example.contains("dog-no-ref"));
    }

    @Test
    void shouldGenerateValidAnyOfCombinationWhenForLevel3Nesting() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/api/some-endpoint", "src/test/resources/issue66.yml");
        Assertions.assertThat(data).hasSize(2);
        Assertions.assertThat(data.get(0).getPayload()).contains("someSubObjectKey3");
        Assertions.assertThat(data.get(1).getPayload()).doesNotContain("someSubObjectKey3");
    }

    @Test
    void shouldGenerateArraySchemaBasedOnMixItemsAndMaxItems() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/api/some-endpoint", "src/test/resources/issue66_2.yml");
        Assertions.assertThat(data).hasSize(2);
        String firstPayload = data.get(0).getPayload();
        int firstArraySize = Integer.parseInt(JsonUtils.getVariableFromJson(firstPayload, "$.someRequestBodyKey1.someObjectKey1.someSubObjectKey1.length()").toString());
        int secondArraySize = Integer.parseInt(JsonUtils.getVariableFromJson(firstPayload, "$.someRequestBodyKey1.someObjectKey1.someSubObjectKey1[0].length()").toString());
        int thirdArraySize = Integer.parseInt(JsonUtils.getVariableFromJson(firstPayload, "$.someRequestBodyKey1.someObjectKey1.someSubObjectKey1[0][0].length()").toString());

        Assertions.assertThat(firstArraySize).isEqualTo(1);
        Assertions.assertThat(secondArraySize).isEqualTo(4);
        Assertions.assertThat(thirdArraySize).isEqualTo(2);
    }


    @Test
    void shouldProperlyParseOneOfWithBaseClass() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/path1", "src/test/resources/oneOf_with_base_class.yml");
        Assertions.assertThat(data).hasSize(2);

        String firstPayload = data.get(0).getPayload();
        Object type = JsonUtils.getVariableFromJson(firstPayload, "$.payload.type");
        Object innerPayload = JsonUtils.getVariableFromJson(firstPayload, "$.payload.payload");

        Assertions.assertThat(type).asString().isEqualTo("Address");
        Assertions.assertThat(innerPayload).asString().isEqualTo("NOT_SET");
    }

    @Test
    void shouldReturnRequiredFieldsWhenAllOfSchemaAndRequiredInRoot() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/allof-with-required-in-root.yml");

        Assertions.assertThat(data).hasSize(1);
        FuzzingData fuzzingData = data.get(0);
        Assertions.assertThat(fuzzingData.getAllRequiredFields()).containsExactly("breed", "id").doesNotContain("color");
    }

    @Test
    void shouldReturnRequiredFieldsWhenAllOfSchemaAndRequiredPropertiesInBothSchemas() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/allof-with-required-for-both-schemas.yml");

        Assertions.assertThat(data).hasSize(1);
        FuzzingData fuzzingData = data.get(0);
        Assertions.assertThat(fuzzingData.getAllRequiredFields()).containsExactly("legs", "breed", "id").doesNotContain("color");
    }

    @Test
    void shouldLoadExample() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pet-types-rec", "src/test/resources/petstore.yml");
        Assertions.assertThat(data.get(0).getExamples()).hasSize(1);
        Assertions.assertThat(data.get(0).getExamples()).anyMatch(example -> example.contains("dog-simple-example"));
    }

    @Test
    void shouldCreateFuzzingDataForEmptyPut() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets/{id}", "src/test/resources/petstore-empty.yml");
        Assertions.assertThat(data).hasSize(1);
        Assertions.assertThat(data.get(0).getMethod()).isEqualByComparingTo(HttpMethod.PUT);
    }

    @Test
    void shouldUseExamplesForPathParams() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets/{id}", "src/test/resources/petstore.yml");
        Assertions.assertThat(data).hasSize(1);
        Assertions.assertThat(data.get(0).getPayload()).contains("78").contains("test");
    }

    @Test
    void shouldNotIncludeReadOnlyFields() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/petstore-readonly.yml");
        Assertions.assertThat(data).hasSize(2);
        FuzzingData postData = data.get(0);
        Assertions.assertThat(postData.getPayload()).doesNotContain("id", "details").contains("age", "data", "name");

        Set<String> allFields = postData.getAllFieldsByHttpMethod();
        Assertions.assertThat(allFields).containsOnly("data#name", "data", "age");
    }

    private List<FuzzingData> setupFuzzingData(String path, String contract) throws IOException {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);

        OpenAPI openAPI = openAPIV3Parser.readContents(Files.readString(Paths.get(contract)), null, options).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application\\/.*\\+?json"));
        catsGlobalContext.getSchemaMap().clear();
        catsGlobalContext.getSchemaMap().putAll(schemas);
        catsGlobalContext.getExampleMap().putAll(OpenApiUtils.getExamples(openAPI));
        catsGlobalContext.getSchemaMap().put(NoMediaType.EMPTY_BODY, NoMediaType.EMPTY_BODY_SCHEMA);
        PathItem pathItem = openAPI.getPaths().get(path);
        return fuzzingDataFactory.fromPathItem(path, pathItem, openAPI);
    }

    @Test
    void shouldCorrectlyParseRefOneOf() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/pet-types", "src/test/resources/petstore.yml");
        Assertions.assertThat(dataList).hasSize(2);
        Assertions.assertThat(dataList.get(0).getPayload()).contains("\"petType\":{\"breedType\"");
        Assertions.assertThat(dataList.get(1).getPayload()).contains("\"petType\":{\"breedType\"");
    }

    @Test
    void shouldGenerateMultiplePayloadsWhenContractGeneratedFromNSwagAndMultipleOneOf() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/api/groopits/create", "src/test/resources/nswag_gen_oneof.json");

        Assertions.assertThat(dataList).hasSize(9);
        FuzzingData firstData = dataList.get(0);
        Assertions.assertThat(firstData.getPayload()).contains("\"discriminator\":\"ResponseData\"");
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        Assertions.assertThat(JsonParser.parseString(firstData.getPayload()).getAsJsonObject().get("Components").isJsonArray()).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenSchemeDoesNotExist() {
        Assertions.assertThatThrownBy(() -> setupFuzzingData("/pet-types", "src/test/resources/petstore-no-schema.yml")).isInstanceOf(IllegalArgumentException.class);
    }
}
