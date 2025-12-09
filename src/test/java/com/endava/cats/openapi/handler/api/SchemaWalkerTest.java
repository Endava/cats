package com.endava.cats.openapi.handler.api;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import jakarta.enterprise.inject.Instance;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@QuarkusTest
class SchemaWalkerTest {
    private List<SchemaLocation> visitedLocations;
    private List<io.swagger.v3.oas.models.media.Schema<?>> visitedSchemas;
    private SchemaHandler testHandler;
    private Instance<SchemaHandler> handlerInstance;

    @BeforeEach
    void setup() {
        visitedLocations = new ArrayList<>();
        visitedSchemas = new ArrayList<>();
        testHandler = (loc, schema) -> {
            visitedLocations.add(loc);
            visitedSchemas.add(schema);
        };
        handlerInstance = Mockito.mock(Instance.class);
        Mockito.when(handlerInstance.stream()).thenReturn(Stream.of(testHandler));
    }

    private OpenAPI loadOpenApi(String resource) {
        String path = Path.of("src/test/resources", resource).toAbsolutePath().toString();
        return new OpenAPIV3Parser().read(path);
    }

    @Test
    void shouldWalkPetstoreYamlAndVisitSchemas() {
        OpenAPI openAPI = loadOpenApi("petstore.yml");
        SchemaWalker.walk(openAPI, testHandler);
        Assertions.assertThat(visitedLocations).isNotEmpty();
        Assertions.assertThat(visitedSchemas).isNotEmpty();
        // Check at least one schema with properties and one with items (array)
        boolean hasProperties = visitedSchemas.stream().anyMatch(s -> s.getProperties() != null && !s.getProperties().isEmpty());
        boolean hasArray = visitedSchemas.stream().anyMatch(s -> s.getItems() != null);
        Assertions.assertThat(hasProperties).isTrue();
        Assertions.assertThat(hasArray).isTrue();
    }

    @Test
    void shouldWalkOpenapiYamlWithComposedSchemas() {
        OpenAPI openAPI = loadOpenApi("token.yml");
        SchemaWalker.walk(openAPI, testHandler);
        boolean hasComposed = visitedSchemas.stream().anyMatch(s ->
                (s.getAllOf() != null && !s.getAllOf().isEmpty()) ||
                        (s.getAnyOf() != null && !s.getAnyOf().isEmpty()) ||
                        (s.getOneOf() != null && !s.getOneOf().isEmpty()));
        Assertions.assertThat(hasComposed).isTrue();
    }

    @Test
    void shouldHandleNullAndEmptySchemasGracefully() {
        // Null OpenAPI
        Assertions.assertThatThrownBy(() -> SchemaWalker.walk(null, testHandler)).isInstanceOf(NullPointerException.class);
        // OpenAPI with no components or paths
        OpenAPI empty = new OpenAPI();
        SchemaWalker.walk(empty, testHandler);
        Assertions.assertThat(visitedLocations).isEmpty();
    }

    @Test
    void shouldInitHandlersCallHandlers() {
        OpenAPI openAPI = loadOpenApi("petstore.yml");
        SchemaWalker walker = new SchemaWalker(handlerInstance);
        walker.initHandlers(openAPI);
        Assertions.assertThat(visitedLocations).isNotEmpty();
    }

    @Test
    void shouldHandleReferencesAndNotSchema() {
        OpenAPI openAPI = loadOpenApi("not-schema.yml");
        SchemaWalker.walk(openAPI, testHandler);
        boolean hasNot = visitedSchemas.stream().anyMatch(s -> s.getNot() != null);
        Assertions.assertThat(visitedSchemas).isNotEmpty();
        Assertions.assertThat(hasNot).isTrue();
    }

    @Test
    void shouldHandleAdditionalProperties() {
        OpenAPI openAPI = loadOpenApi("token.yml");
        SchemaWalker.walk(openAPI, testHandler);
        boolean hasAdditional = visitedSchemas.stream().anyMatch(s -> s.getAdditionalProperties() != null);
        Assertions.assertThat(hasAdditional).isTrue();
    }

    @Test
    void shouldPropagatePathAndMethodContextForOperationSchemas() {
        OpenAPI openAPI = loadOpenApi("petstore.yml");
        SchemaWalker.walk(openAPI, testHandler);

        // Schemas from paths should have non-null path and method
        boolean hasOperationContext = visitedLocations.stream()
                .anyMatch(loc -> loc.path() != null && loc.method() != null);
        Assertions.assertThat(hasOperationContext).isTrue();

        // Global schemas (from components) should have null path and method
        boolean hasGlobalContext = visitedLocations.stream()
                .anyMatch(SchemaLocation::isGlobalLocation);
        Assertions.assertThat(hasGlobalContext).isTrue();
    }
}
