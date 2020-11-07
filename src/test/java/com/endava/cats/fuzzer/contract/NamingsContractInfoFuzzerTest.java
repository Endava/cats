package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class NamingsContractInfoFuzzerTest {

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;

    private NamingsContractInfoFuzzer namingsContractInfoFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
        namingsContractInfoFuzzer = new NamingsContractInfoFuzzer(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"/petsPath", "/pets_path", "/pets-path-link", "/pets/Paths", "/pets/complex-Paths", "/pets/{petid10}", "/pets/{pet-id}"})
    void shouldReportError(String path) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        pathItem.setPost(operation);
        FuzzingData data = FuzzingData.builder().path(path).method(HttpMethod.POST).pathItem(pathItem).reqSchemaName("Cats").build();

        namingsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.eq("Path does not follow REST naming good practices: {}"), Mockito.contains(path.substring(path.lastIndexOf("/") + 1)));
    }

    @ParameterizedTest
    @CsvSource({"/pets-paths", "/pets_paths", "/pets-path-links", "/pets/paths", "/pets/complex-paths", "/pets/{petId}", "/pets/{pet_id}"})
    void shouldReportInfo(String path) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        pathItem.setPost(operation);
        FuzzingData data = FuzzingData.builder().path(path).method(HttpMethod.POST).pathItem(pathItem).reqSchemaName("Cats").build();

        namingsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.eq("Path follows the REST naming good practices."));
    }

    @Test
    void shouldReportErrorWhenJsonObjectsNotMatchingCamelCase() {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        ApiResponses apiResponses = new ApiResponses();

        ApiResponse firstApiResponse = new ApiResponse();
        firstApiResponse.set$ref("#components/first_Payload-test");
        apiResponses.addApiResponse("200", firstApiResponse);

        ApiResponse secondApiResponse = new ApiResponse();
        Content content = new Content();
        MediaType mediaType = new MediaType();
        Schema schema = new Schema();
        schema.set$ref("#components/secondpayload_tesAaa");
        mediaType.setSchema(schema);
        content.put("application/json", mediaType);
        secondApiResponse.setContent(content);
        apiResponses.addApiResponse("300", secondApiResponse);

        operation.setResponses(apiResponses);
        pathItem.setPost(operation);
        FuzzingData data = FuzzingData.builder().path("/pets").method(HttpMethod.POST).pathItem(pathItem).reqSchemaName("Cats").build();

        namingsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.eq("Path does not follow REST naming good practices: {}"), Mockito.contains("The following request/response objects are not matching CamelCase, snake_case or hyphen-case: <strong>first_Payload-test</strong>, <strong>secondpayload_tesAaa</strong><br /><br />"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(namingsContractInfoFuzzer).hasToString(namingsContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(namingsContractInfoFuzzer.description()).isEqualTo("verifies that all OpenAPI contract elements follow REST API naming good practices");
    }
}
