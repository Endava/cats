package com.endava.cats.context;

import com.endava.cats.factory.NoMediaType;
import com.endava.cats.openapi.OpenApiUtils;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Holds global variables which should not be recomputed for each path.
 */
@Singleton
@Getter
public class CatsGlobalContext {
    public static final String ORIGINAL = "Original";
    private final Map<String, Schema> schemaMap = new HashMap<>();
    private final Map<String, Example> exampleMap = new HashMap<>();
    private final Map<String, Schema> requestDataTypes = new HashMap<>();
    private final List<String> additionalProperties = new ArrayList<>();
    private final List<Discriminator> discriminators = new ArrayList<>();
    private final Map<String, Deque<String>> postSuccessfulResponses = new HashMap<>();
    private final Set<String> successfulDeletes = new HashSet<>();
    private final Properties fuzzersConfiguration = new Properties();
    private final Map<String, List<String>> generatedExamplesCache = new HashMap<>();
    private CatsConfiguration catsConfiguration;
    @Setter
    private OpenAPI openAPI;

    /**
     * Returns the expected HTTP response code from the --fuzzConfig file
     *
     * @param fuzzer the name of the fuzzer
     * @return the value of the property if found or null otherwise
     */
    public String getExpectedResponseCodeConfigured(String fuzzer) {
        return this.fuzzersConfiguration.getProperty(fuzzer);
    }

    public void init(OpenAPI openAPI, List<String> contentType, Properties fuzzersConfiguration, CatsConfiguration catsConfiguration) {
        Map<String, Schema> allSchemasFromOpenApi = OpenApiUtils.getSchemas(openAPI, contentType);
        this.getSchemaMap().putAll(allSchemasFromOpenApi);
        this.getSchemaMap().put(NoMediaType.EMPTY_BODY, NoMediaType.EMPTY_BODY_SCHEMA);
        this.getExampleMap().putAll(OpenApiUtils.getExamples(openAPI));
        this.getFuzzersConfiguration().putAll(fuzzersConfiguration);

        //sometimes OpenAPI generator adds a "" entry
        this.getSchemaMap().remove("");
        this.catsConfiguration = catsConfiguration;
        this.openAPI = openAPI;
    }

    /**
     * Checks if the example for a given key has already been generated
     *
     * @param key the key to check
     * @return true if the example has already been generated, false otherwise
     */
    public boolean isExampleAlreadyGenerated(String key) {
        return this.generatedExamplesCache.containsKey(key);
    }

    /**
     * Adds a generated example to the cache
     *
     * @param key      the key to add
     * @param examples the examples to add
     */
    public void addGeneratedExample(String key, List<String> examples) {
        this.generatedExamplesCache.put(key, examples);
    }

    /**
     * Adds a schema to the global context.
     *
     * @param key    the key to add
     * @param schema the schema to add
     */
    public void putSchemaReference(String key, Schema<?> schema) {
        schemaMap.put(key, schema);
    }

    /**
     * Gets a schema from the global context by reference. The reference can be a simple key or a path to a schema in the OpenAPI document.
     * If the reference is a simple key, the schema is retrieved from the schemaMap. If the reference is a path, the schema is traversed.
     *
     * @param reference the reference to get
     * @return the schema if found, null otherwise
     */
    public Schema<?> getSchemaFromReference(String reference) {
        Schema<?> result = getSchemaFromSimpleReferenceName(reference);

        if (reference.startsWith("#/components") || reference.startsWith("#/definitions")) {
            result = getSchemaFromComponentsDefinitions(reference);
        }

        if (reference.startsWith("#/components") && result == null) {
            result = (Schema<?>) getObjectFromPathsReference(reference);
        }

        if (reference.contains("#/paths")) {
            String shortRef = reference.substring(reference.lastIndexOf("#/paths"));
            result = (Schema<?>) getObjectFromPathsReference(shortRef);
        }

        if (result != null && result.get$ref() != null && !result.get$ref().endsWith(NoMediaType.EMPTY_BODY)) {
            result = getSchemaFromReference(result.get$ref());
        }

        this.schemaMap.putIfAbsent(CatsModelUtils.getSimpleRef(reference), result);
        return result;
    }

    /**
     * Gets a path item from the global context by reference.
     *
     * @param reference the reference to get
     * @return the path item if found, null otherwise
     */
    public PathItem getPathItemFromReference(String reference) {
        return (PathItem) getObjectFromPathsReference(reference);
    }

    /**
     * Gets an ApiResponse from the global context by reference.
     *
     * @param reference the reference to get
     * @return the ApiResponse if found, null otherwise
     */
    public Object getApiResponseFromReference(String reference) {
        return getObjectFromPathsReference(reference);
    }

    public Object getObjectFromPathsReference(String reference) {
        String jsonPointer = reference.substring(2);
        String[] parts = jsonPointer.split("/");

        Object resolvedDefinition = openAPI;

        for (String part : parts) {
            part = URLDecoder.decode(part.replace("~1", "/"), StandardCharsets.UTF_8);
            String finalPart = part;
            resolvedDefinition = switch (resolvedDefinition) {
                case Map<?, ?> map -> map.get(finalPart);
                case OpenAPI ignored when finalPart.equals("paths") -> openAPI.getPaths();
                case PathItem item ->
                        item.readOperationsMap().get(PathItem.HttpMethod.valueOf(finalPart.toUpperCase(Locale.ROOT)));
                case Operation operation -> extractFromOperation(finalPart, operation, resolvedDefinition);
                case RequestBody requestBody when finalPart.equals("content") -> requestBody.getContent();
                case ApiResponse apiResponse -> extractFromApiResponse(finalPart, apiResponse, resolvedDefinition);
                case MediaType mediaType when "schema".equals(finalPart) -> mediaType.getSchema();
                case Schema<?> schema -> extractFromSchema(finalPart, schema, resolvedDefinition);
                case List<?> asList -> asList.get(Integer.parseInt(finalPart));
                case Object ignored when "components".equals(finalPart) -> openAPI.getComponents();
                case Components components when "schemas".equals(finalPart) -> components.getSchemas();
                default -> null;
            };

            if (resolvedDefinition == null) break;
        }

        return resolvedDefinition;
    }

    private Object extractFromApiResponse(String part, ApiResponse apiResponse, Object resolvedDefinition) {
        if ("content".equals(part)) {
            resolvedDefinition = apiResponse.getContent();
        } else if ("headers".equals(part)) {
            resolvedDefinition = apiResponse.getHeaders();
        }
        return resolvedDefinition;
    }

    private Object extractFromOperation(String part, Operation operation, Object resolvedDefinition) {
        if (part.equalsIgnoreCase("requestBody")) {
            resolvedDefinition = operation.getRequestBody();
        } else if (part.equals("responses")) {
            resolvedDefinition = operation.getResponses();
        } else if (part.equals("parameters")) {
            resolvedDefinition = operation.getParameters();
        }
        return resolvedDefinition;
    }

    private Object extractFromSchema(String part, Schema<?> schema, Object resolvedDefinition) {
        String initialSchemaRef = schema.get$ref();
        if (schema.get$ref() != null) {
            schema = getSchemaFromReference(schema.get$ref());
        }

        List<String> ofs = List.of("allof", "oneof", "anyof");

        if ("properties".equals(part)) {
            resolvedDefinition = schema.getProperties();
        } else if ("items".equalsIgnoreCase(part)) {
            resolvedDefinition = schema.getItems();
        }

        if (ofs.contains(part.toLowerCase(Locale.ROOT))) {
            if (!CatsModelUtils.isComposedSchema(schema)) {
                schema = getSchemaFromReference(initialSchemaRef + ORIGINAL);
            }

            if ("allof".equalsIgnoreCase(part)) {
                resolvedDefinition = schema.getAllOf();
            } else if ("anyof".equalsIgnoreCase(part)) {
                resolvedDefinition = schema.getAnyOf();
            } else if ("oneof".equalsIgnoreCase(part)) {
                resolvedDefinition = schema.getOneOf();
            }
        }

        return resolvedDefinition;
    }

    private Schema getSchemaFromSimpleReferenceName(String reference) {
        return schemaMap.get(reference);
    }

    private Schema getSchemaFromComponentsDefinitions(String reference) {
        return schemaMap.get(CatsModelUtils.getSimpleRef(reference));
    }
}
