package com.endava.cats.generator.simple;

import com.endava.cats.openapi.OpenApiUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
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
        Assertions.assertThat(schemaMap.keySet()).contains("ThePet");

        Schema<?> badRequest = schemaMap.get("BadRequest");
        Assertions.assertThat(badRequest).isNotNull();
        Assertions.assertThat(badRequest.getProperties()).isNull();
    }
}
