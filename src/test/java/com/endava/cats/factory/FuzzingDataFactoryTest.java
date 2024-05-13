package com.endava.cats.factory;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsField;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.OpenApiUtils;
import com.endava.cats.util.JsonUtils;
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
    private FilterArguments filterArguments;
    private FuzzingDataFactory fuzzingDataFactory;

    @BeforeEach
    void setup() {
        filesArguments = Mockito.mock(FilesArguments.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        filterArguments = Mockito.mock(FilterArguments.class);
        Mockito.when(processingArguments.isUseExamples()).thenReturn(true);
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(10);
        Mockito.when(processingArguments.isGenerateAllXxxCombinationsForResponses()).thenReturn(true);
        Mockito.when(processingArguments.isFilterXxxFromRequestPayloads()).thenReturn(false);
        Mockito.when(processingArguments.getContentType()).thenReturn(List.of(ProcessingArguments.JSON_WILDCARD, "application/x-www-form-urlencoded"));
        fuzzingDataFactory = new FuzzingDataFactory(filesArguments, processingArguments, catsGlobalContext, validDataFormat, filterArguments);
    }

    @Test
    void shouldFilterOutXxxExamples() throws Exception {
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(2);
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);

        List<FuzzingData> dataList = setupFuzzingData("/api/v2/meta/tables/{tableId}/columns", "src/test/resources/nocodb.yaml");

        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData firstData = dataList.get(1);
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF");
    }

    @Test
    void shouldGenerateMultiplePayloadsWhenRootOneOfAndDiscriminatorAndAllOfAndMappings() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/variant", "src/test/resources/issue_69.yml");
        Assertions.assertThat(data).hasSize(1);

        List<String> responses = data.get(0).getResponses().get("200");
        Assertions.assertThat(responses).hasSize(2);
        Assertions.assertThat(responses.get(0)).doesNotContain("Variant").contains("option1");
        Assertions.assertThat(responses.get(1)).doesNotContain("Variant").contains("option2");
    }

    @Test
    void givenAContract_whenParsingThePathItemDetailsForPost_thenCorrectFuzzingDataAreBeingReturned() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/petstore.yml");

        Assertions.assertThat(data).hasSizeGreaterThanOrEqualTo(2);

        Assertions.assertThat(data.get(0).getPayload()).doesNotContain("ONE_OF", "ANY_OF");
        Assertions.assertThat(data.get(1).getPayload()).doesNotContain("ONE_OF", "ANY_OF");
    }

    @Test
    void shouldIgnoreOneOfAnyOfWhenAdditionalSchemaIsNull() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/path1", "src/test/resources/oneOf_with_null_additional.yml");
        Assertions.assertThat(data).hasSize(1);

        Assertions.assertThat(data.get(0).getPayload()).contains("dateFrom");
        Assertions.assertThat(data.get(0).getPayload()).doesNotContain("ONE_OF", "ANY_OF");
    }

    @Test
    void shouldNotGenerateRequestBodyWhenPostButSchemaEmpty() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/petstore_empty_body.json");
        Assertions.assertThat(data).hasSize(1);
        Assertions.assertThat(data.get(0).getMethod()).isEqualTo(HttpMethod.GET);
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
        Assertions.assertThat(data.get(1).getPayload()).contains("someSubObjectKey3");
        Assertions.assertThat(data.get(0).getPayload()).doesNotContain("someSubObjectKey3");
    }

    @Test
    void shouldGenerateArraySchemaBasedOnMixItemsAndMaxItems() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/api/some-endpoint", "src/test/resources/issue66_2.yml");
        Assertions.assertThat(data).hasSize(2);
        String firstPayload = data.get(1).getPayload();
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
        catsGlobalContext.getSchemaMap().remove("");
        PathItem pathItem = openAPI.getPaths().get(path);

        Mockito.when(filesArguments.isNotUrlParam(Mockito.anyString())).thenReturn(true);
        return fuzzingDataFactory.fromPathItem(path, pathItem, openAPI);
    }

    @Test
    void shouldCorrectlyParseRefOneOf() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/pet-types", "src/test/resources/petstore.yml");
        Assertions.assertThat(dataList).hasSize(2);
        Assertions.assertThat(dataList.get(0).getPayload()).contains("\"petType\":{\"breedType\":\"Husky");
        Assertions.assertThat(dataList.get(1).getPayload()).contains("\"petType\":{\"breedType\":\"Labrador");
    }

    @Test
    void shouldGenerateMultiplePayloadsWhenContractGeneratedFromNSwagAndMultipleOneOf() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/api/groopits/create", "src/test/resources/nswag_gen_oneof.json");

        Assertions.assertThat(dataList).hasSize(9);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));
        FuzzingData firstData = dataList.get(7);
        Assertions.assertThat(firstData.getPayload()).containsAnyOf("\"discriminator\":\"ResponseData\"", "\"discriminator\":\"PictureData\"");
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        Assertions.assertThat(JsonParser.parseString(firstData.getPayload()).getAsJsonObject().get("Components").isJsonArray()).isTrue();
    }

    @Test
    void shouldGenerateWhenAllOfHaveOnlyASingleSubject() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/datasets/{datasetId}", "src/test/resources/keatext.yaml");

        Assertions.assertThat(dataList).hasSize(3);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));
        FuzzingData firstData = dataList.get(0);
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        String keyWithSquares = String.valueOf(JsonUtils.getVariableFromJson(firstData.getPayload(), "$.primaryDate"));

        Assertions.assertThat(keyWithSquares).isNotEqualTo("NOT_SET");
    }

    @Test
    void shouldProperlyParseKeysWithSquareBrackets() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/tags", "src/test/resources/getresp.yaml");

        Assertions.assertThat(dataList).hasSize(5);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));
        FuzzingData firstData = dataList.get(1);
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        String keyWithSquares = String.valueOf(JsonUtils.getVariableFromJson(firstData.getPayload(), "$.query[createdAt][to]"));

        Assertions.assertThat(keyWithSquares).isNotEqualTo("NOT_SET");
    }

    @Test
    void shouldProperlyCreateAllXxxCombinations() throws Exception {
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(100);
        List<FuzzingData> dataList = setupFuzzingData("/shipments", "src/test/resources/shippo.yaml");

        Assertions.assertThat(dataList).hasSize(77);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .filteredOn(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"))
                .hasSize(4);
    }

    @Test
    void shouldProperlyGenerateFromArrayWithAnyOfElements() throws Exception {
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(50);
        List<FuzzingData> dataList = setupFuzzingData("/api/v1/studies", "src/test/resources/prolific.yaml");

        Assertions.assertThat(dataList).hasSize(31);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .filteredOn(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"))
                .hasSize(1);

        FuzzingData firstData = dataList.get(0);
        boolean isActionsArray = JsonUtils.isArray(firstData.getPayload(), "$.CreateStudy.completion_codes[0].actions");
        Assertions.assertThat(isActionsArray).isTrue();
    }

    @Test
    void shouldLimitXxxCombinationsWhenCombinationsExceedArgument() throws Exception {
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(100);
        List<FuzzingData> dataList = setupFuzzingData("/v0/organizations/{organization-id}/onboarding/applications", "src/test/resources/griffin.yaml");

        Assertions.assertThat(dataList).hasSize(100);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .filteredOn(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"))
                .hasSize(3);

        FuzzingData firstData = dataList.get(0);
        boolean isActionsArray = JsonUtils.isArray(firstData.getPayload(), "$.subject-profile.claims");
        Assertions.assertThat(isActionsArray).isTrue();
    }

    @Test
    void shouldGenerateCombinationWhenXxxArraysAndSimpleTypes() throws IOException {
        List<FuzzingData> dataList = setupFuzzingData("/api/database/tokens/{token_id}", "src/test/resources/baserow.yaml");

        Assertions.assertThat(dataList).hasSize(12);

        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));

        FuzzingData data = dataList.get(4);
        Object createPermision = JsonUtils.getVariableFromJson(data.getPayload(), "$.permissions.create[0]");
        Object updatePermission = JsonUtils.getVariableFromJson(data.getPayload(), "$.permissions.update[0]");

        Assertions.assertThat(createPermision).asString().isEqualTo("database");
        Assertions.assertThat(updatePermission).asString().isEqualTo("database");
    }

    @Test
    void shouldGenerateCombinationsWhenXxxAsInlineSchemas() throws IOException {
        List<FuzzingData> dataList = setupFuzzingData("/v1/employee_benefits/{employee_benefit_id}", "src/test/resources/gusto.yaml");

        Assertions.assertThat(dataList).hasSize(4);

        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));

        FuzzingData firstData = dataList.get(1);
        boolean firstDataIsArray = JsonUtils.isArray(firstData.getPayload(), "$.contribution.value");

        FuzzingData zeroData = dataList.get(0);
        boolean zeroDataIsNotArray = JsonUtils.isArray(zeroData.getPayload(), "$.contribution.value");

        Assertions.assertThat(firstDataIsArray).isTrue();
        Assertions.assertThat(zeroDataIsNotArray).isFalse();
    }

    @Test
    void shouldProperlyParseRootAllOfAndOneOfElements() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/payouts", "src/test/resources/token.yml");

        Assertions.assertThat(dataList).hasSize(11);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));
        FuzzingData firstData = dataList.get(0);
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        Object creditorNotExistent = JsonUtils.getVariableFromJson(firstData.getPayload(), "$.initiation.creditor.creditor");
        String bicValue = String.valueOf(JsonUtils.getVariableFromJson(firstData.getPayload(), "$.initiation.creditor.sortCode"));

        Assertions.assertThat(creditorNotExistent).hasToString("NOT_SET");
        Assertions.assertThat(bicValue).isNotEqualTo("NOT_SET");
    }

    @Test
    void shouldProperlyGenerateOneOfAnyOfPayloads() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/pets-batch", "src/test/resources/petstore.yml");

        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData firstData = dataList.get(0);
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        Assertions.assertThat(JsonParser.parseString(firstData.getPayload()).isJsonArray()).isTrue();
    }

    @Test
    void shouldFilterDeprecatedOperations() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/petstore-deprecated-tags.yml");

        Assertions.assertThat(dataList).hasSize(4);
        Assertions.assertThat(dataList.get(0).getMethod()).isEqualTo(HttpMethod.POST);
        Assertions.assertThat(dataList.get(1).getMethod()).isEqualTo(HttpMethod.POST);
        Assertions.assertThat(dataList.get(2).getMethod()).isEqualTo(HttpMethod.GET);
        Assertions.assertThat(dataList.get(3).getMethod()).isEqualTo(HttpMethod.DELETE);

        Mockito.when(filterArguments.isSkipDeprecated()).thenReturn(true);
        List<FuzzingData> secondTimeFuzzDataList = setupFuzzingData("/pets", "src/test/resources/petstore-deprecated-tags.yml");
        Assertions.assertThat(secondTimeFuzzDataList).hasSize(1);
        Assertions.assertThat(secondTimeFuzzDataList.get(0).getMethod()).isEqualTo(HttpMethod.GET);
    }

    @Test
    void shouldOnlyIncludeProvidedTags() throws Exception {
        Mockito.when(filterArguments.getTags()).thenReturn(List.of("pets"));
        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/petstore-deprecated-tags.yml");

        Assertions.assertThat(dataList).hasSize(2);
        Assertions.assertThat(dataList.get(0).getMethod()).isEqualTo(HttpMethod.GET);
        Assertions.assertThat(dataList.get(1).getMethod()).isEqualTo(HttpMethod.DELETE);
    }

    @Test
    void shouldSkipProvidedTags() throws Exception {
        Mockito.when(filterArguments.getSkippedTags()).thenReturn(List.of("pets"));
        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/petstore-deprecated-tags.yml");

        Assertions.assertThat(dataList).hasSize(2);
        Assertions.assertThat(dataList.get(0).getMethod()).isEqualTo(HttpMethod.POST);
        Assertions.assertThat(dataList.get(1).getMethod()).isEqualTo(HttpMethod.POST);
    }

    @Test
    void testAnyOfPrimitiveTypes() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/mfm/v1/services/", "src/test/resources/issue86.json");

        Assertions.assertThat(dataList).hasSize(5);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));
        FuzzingData firstData = dataList.get(0);
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        List<String> responses = dataList.get(0).getResponses().get("422");
        Assertions.assertThat(responses).hasSize(2);
        String responsePayload = responses.get(0);
        Assertions.assertThat(responsePayload).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF").contains("loc");
    }

    @Test
    void shouldLimitOneOfAnyOfCombinations() throws Exception {
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(1);
        List<FuzzingData> dataList = setupFuzzingData("/mfm/v1/services/", "src/test/resources/issue86.json");
        Assertions.assertThat(dataList).hasSize(2);
    }

    @Test
    void shouldGenerateValidResponseForOneOfNestedCombinations() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/api/groops/{groopId}/StartGroopitPaging", "src/test/resources/nswag_gen_oneof.json");
        Assertions.assertThat(dataList).hasSize(1);
        FuzzingData firstData = dataList.get(0);
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        Assertions.assertThat(firstData.getResponses().get("200")).hasSize(9);
        String firstResponse = firstData.getResponses().get("200").get(0);
        Assertions.assertThat(firstResponse).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
    }

    @Test
    void shouldGenerateValidDataWhenPrimitiveArrays() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/issue94.json");
        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData firstData = dataList.get(0);
        Map<String, List<String>> responses = firstData.getResponses();
        Assertions.assertThat(responses).hasSize(2);
        String defaultResponses = responses.get("default").get(0);
        boolean isArray = JsonUtils.isArray(defaultResponses, "$.code");
        Assertions.assertThat(isArray).isTrue();
    }

    @Test
    void shouldProperlyGenerateArraysWhenElementsUsingXXXOf() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/path2", "src/test/resources/oneOf_with_base_class.yml");
        Assertions.assertThat(data).hasSize(2);
        String payload = data.get(1).getPayload();

        Object variable = JsonUtils.getVariableFromJson(payload, "$.payloads[0].payloads");
        Assertions.assertThat(variable).hasToString("NOT_SET");

        Object firstElementType = JsonUtils.getVariableFromJson(payload, "$.payloads[0].type");
        Assertions.assertThat(firstElementType).hasToString("Address");
    }

    @Test
    void shouldThrowExceptionWhenSchemeDoesNotExist() {
        Assertions.assertThatThrownBy(() -> setupFuzzingData("/pet-types", "src/test/resources/petstore-no-schema.yml")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldGetAllFieldsWhenSchemaDoesNotExist() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/rest/1/pdf/", "src/test/resources/issue98.json");
        Assertions.assertThat(dataList).hasSize(1);
        FuzzingData data = dataList.get(0);
        Set<CatsField> fields = data.getAllFieldsAsCatsFields();
        Assertions.assertThat(fields).hasSize(4);
    }

    @Test
    void shouldDetectCyclicDependenciesWhenPropertiesNamesDontMatch() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/issue117.json");
        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData data = dataList.get(0);
        String var1 = String.valueOf(JsonUtils.getVariableFromJson(data.getPayload(), "$.credentialSource.updatedBy"));
        Object var2 = JsonUtils.getVariableFromJson(data.getPayload(), "$.credentialSource.updatedBy.credentialSource");
        Assertions.assertThat(var1).isNotEqualTo("NOT_SET");
        Assertions.assertThat(var2).hasToString("NOT_SET");
    }

    @Test
    void shouldConsiderSelfReferenceDepthWhenDetectingCyclicDependencies() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);
        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/issue117.json");
        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData data = dataList.get(0);
        String var1 = String.valueOf(JsonUtils.getVariableFromJson(data.getPayload(), "$.credentialSource.updatedBy.credentialSource.updatedBy.credentialSource.updatedBy"));
        Object var2 = JsonUtils.getVariableFromJson(data.getPayload(), "$.credentialSource.updatedBy.credentialSource.updatedBy.credentialSource.updatedBy.credentialSource");
        Assertions.assertThat(var1).isNotEqualTo("NOT_SET");
        Assertions.assertThat(var2).hasToString("NOT_SET");
    }

    @Test
    void shouldGenerateOpenApi31Specs() throws Exception {
        System.getProperties().setProperty("bind-type", "true");
        List<FuzzingData> dataList = setupFuzzingData("/pet", "src/test/resources/petstore31.yaml");
        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData data = dataList.get(0);
        List<String> fields = data.getAllFieldsAsCatsFields().stream().map(CatsField::getName).toList();
        Assertions.assertThat(fields).containsExactly("status",
                "legs",
                "tags",
                "photoUrls",
                "name",
                "id",
                "category#name",
                "category",
                "age",
                "category#id",
                "residency");
    }
}
