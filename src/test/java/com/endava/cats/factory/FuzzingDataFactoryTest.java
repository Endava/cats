package com.endava.cats.factory;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.OpenApiUtils;
import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;
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
    private FilesArguments filesArguments;
    private ProcessingArguments processingArguments;
    private FuzzingDataFactory fuzzingDataFactory;

    @BeforeEach
    void setup() {
        filesArguments = Mockito.mock(FilesArguments.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        Mockito.when(processingArguments.isUseExamples()).thenReturn(true);
        Mockito.when(processingArguments.getContentType()).thenReturn(List.of("application/json", "application/x-www-form-urlencoded"));
        fuzzingDataFactory = new FuzzingDataFactory(filesArguments, processingArguments, catsGlobalContext);
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
