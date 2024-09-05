package com.endava.cats.openapi;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class OpenApiUtilsTest {

    @Test
    void shouldReadFromHttpLocationAndReturnNll() throws Exception {
        String location = "http://google.com/opn.yml";

        OpenAPI openAPI = OpenApiUtils.readOpenApi(location);
        Assertions.assertThat(openAPI).isNull();
    }

    @Test
    void shouldReturnProvidedContentType() {
        Content content = Mockito.mock(Content.class);
        MediaType mediaType = Mockito.mock(MediaType.class);
        Mockito.when(content.get("app/json")).thenReturn(mediaType);

        MediaType result = OpenApiUtils.getMediaTypeFromContent(content, "app/json");
        Assertions.assertThat(result).isEqualTo(mediaType);
    }

    @Test
    void shouldReturnDefaultContentType() {
        Content content = Mockito.mock(Content.class);
        MediaType mediaType = Mockito.mock(MediaType.class);
        Mockito.when(content.get("app/json")).thenReturn(mediaType);

        MediaType result = OpenApiUtils.getMediaTypeFromContent(content, "non-app/json");
        Assertions.assertThat(result).isNull();
    }

    @Test
    void shouldReturnContentTypeWhenContainingCharset() {
        Content content = Mockito.mock(Content.class);
        MediaType mediaType = Mockito.mock(MediaType.class);
        MediaType mediaTypeCharset = Mockito.mock(MediaType.class);

        Mockito.when(content.get("app/json")).thenReturn(mediaType);
        Mockito.when(content.entrySet()).thenReturn(Set.of(new AbstractMap.SimpleEntry<>("app/json2", mediaTypeCharset)));
        MediaType actual = OpenApiUtils.getMediaTypeFromContent(content, "app/json2");
        Assertions.assertThat(actual).isSameAs(mediaTypeCharset).isNotSameAs(mediaType);
    }

    @Test
    void shouldReturnTrueWhenContentTypeWithCharset() {
        Content content = Mockito.mock(Content.class);
        Mockito.when(content.keySet()).thenReturn(Set.of("application/json;charset=UTF-8", "application/v1+json"));

        boolean actual = OpenApiUtils.hasContentType(content, List.of("application/v1+json", "application\\/.*\\+?json;?.*"));
        Assertions.assertThat(actual).isTrue();
    }

    @Test
    void shouldReturnFalseWhenContentTypeWithCharset() {
        Content content = Mockito.mock(Content.class);
        Mockito.when(content.keySet()).thenReturn(Set.of("application/hml"));

        boolean actual = OpenApiUtils.hasContentType(content, List.of("application/v1+json", "application\\/.*\\+?json;?.*"));
        Assertions.assertThat(actual).isFalse();
    }

    @Test
    void shouldNotParseContentType() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/petstore-invalid-content.yml");
        Map<String, Schema> schemaMap = OpenApiUtils.getSchemas(openAPI, List.of("application\\/.*\\+?json;?.*"));
        Assertions.assertThat(schemaMap).containsKey("ThePet");

        Schema<?> badRequest = schemaMap.get("BadRequest");
        Assertions.assertThat(badRequest).isNotNull();
        Assertions.assertThat(badRequest.getProperties()).isNull();
    }

    @Test
    void shouldReturnRequestBodies() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> requestBodies = OpenApiUtils.getRequestBodies(openAPI);

        Assertions.assertThat(requestBodies).containsExactly("UserArray", "PetB");
    }

    @Test
    void shouldReturnComponentsSchemas() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> requestBodies = OpenApiUtils.getSchemas(openAPI);

        Assertions.assertThat(requestBodies).containsExactly("Order", "Category", "User", "Tag", "Pet", "ApiResponse", "body", "body_1");
    }

    @Test
    void shouldReturnResponses() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/petstore.yml");
        Set<String> requestBodies = OpenApiUtils.getResponses(openAPI);

        Assertions.assertThat(requestBodies).containsExactly("BadRequest");
    }

    @Test
    void shouldReturnSecuritySchemas() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> requestBodies = OpenApiUtils.getSecuritySchemes(openAPI);

        Assertions.assertThat(requestBodies).containsExactly("petstore_auth", "api_key");
    }

    @Test
    void shouldReturnAllParameters() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> requestBodies = OpenApiUtils.getParameters(openAPI);

        Assertions.assertThat(requestBodies).containsExactly("offsetParam");
    }

    @Test
    void shouldReturnHeadersFromComponents() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> requestBodies = OpenApiUtils.getHeaders(openAPI);

        Assertions.assertThat(requestBodies).containsExactly("Custom-Header", "Another-Header");
    }

    @Test
    void shouldReturnServers() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/petstore.yml");
        Set<String> requestBodies = OpenApiUtils.getServers(openAPI);

        Assertions.assertThat(requestBodies).containsExactly("http://petstore.swagger.io/api - missing description");
    }

    @Test
    void shouldReturnDeprecatedHeaders() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> requestBodies = OpenApiUtils.getDeprecatedHeaders(openAPI);

        Assertions.assertThat(requestBodies).containsExactly("X-Header-Dep");
    }

    @Test
    void shouldGetInfo() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Info info = OpenApiUtils.getInfo(openAPI);

        Assertions.assertThat(info.getTitle()).isEqualTo("OpenAPI Petstore");
    }

    @Test
    void shouldDocumentationUrl() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        String url = OpenApiUtils.getDocumentationUrl(openAPI);

        Assertions.assertThat(url).isEqualTo("https://openapi-generator.tech");
    }

    @Test
    void shouldGetExtensions() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> extensions = OpenApiUtils.getExtensions(openAPI);

        Assertions.assertThat(extensions).containsExactly("x-vendor");
    }

    @Test
    void shouldReturnDeprecatedOperations() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> extensions = OpenApiUtils.getDeprecatedOperations(openAPI);

        Assertions.assertThat(extensions).containsExactly("findPetsByTags");
    }

    @ParameterizedTest
    @CsvSource({"petstore.yml,0", "openapi.yml,1"})
    void shouldReturnMonitoringEndpoints(String contract, int endpoints) throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/" + contract);
        Set<String> extensions = OpenApiUtils.getMonitoringEndpoints(openAPI);

        Assertions.assertThat(extensions).hasSize(endpoints);
    }

    @Test
    void shouldReturnMissingPaginationSupport() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> extensions = OpenApiUtils.getPathsMissingPaginationSupport(openAPI);

        Assertions.assertThat(extensions).containsExactly("/pet/findByStatus", "/user/login", "/pet/findByTags");
    }

    @Test
    void shouldReturnAllProducesHeaders() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> extensions = OpenApiUtils.getAllProducesHeaders(openAPI);

        Assertions.assertThat(extensions).containsExactly("application/xml", "application/json");
    }

    @Test
    void shouldReturnAllConsumesHeaders() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> extensions = OpenApiUtils.getAllConsumesHeaders(openAPI);

        Assertions.assertThat(extensions).containsExactly("application/json", "multipart/form-data", "application/x-www-form-urlencoded");
    }

    @Test
    void shouldReturnAllResponseCodes() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> extensions = OpenApiUtils.getAllResponseCodes(openAPI);

        Assertions.assertThat(extensions).containsExactly("200", "400", "404", "405", "default");
    }

    @Test
    void shouldReturnAllHttpMethods() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> extensions = OpenApiUtils.getUsedHttpMethods(openAPI);

        Assertions.assertThat(extensions).containsExactly("DELETE", "POST", "GET", "PUT", "PATCH");
    }

    @Test
    void shouldSearchHeaders() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> extensions = OpenApiUtils.searchHeader(openAPI, "apikey", "none");

        Assertions.assertThat(extensions).containsExactly("api_key");
    }

    @Test
    void shouldGetAllTags() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> extensions = OpenApiUtils.getAllTags(openAPI);

        Assertions.assertThat(extensions).containsExactly("store", "user", "pet");
    }

    @Test
    void shouldGetApiVersions() throws Exception {
        OpenAPI openAPI = OpenApiUtils.readOpenApi("src/test/resources/openapi.yml");
        Set<String> extensions = OpenApiUtils.getApiVersions(openAPI);

        Assertions.assertThat(extensions).containsExactly("v3");
    }

    @ParameterizedTest
    @CsvSource({"/pets/{id},id", "/pets/{id}/status,id", "/pets/{id}/status/{status},id|status"})
    void shouldGetPathVariables(String path, String variables) {
        Set<String> pathVariables = OpenApiUtils.getPathVariables(path);

        Assertions.assertThat(pathVariables).containsExactlyInAnyOrder(variables.split("\\|"));
    }

}
