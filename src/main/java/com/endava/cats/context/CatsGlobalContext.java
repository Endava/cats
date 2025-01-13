package com.endava.cats.context;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsConfiguration;
import com.endava.cats.model.NoMediaType;
import com.endava.cats.model.ProcessingError;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.OpenApiUtils;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Holds global variables which should not be recomputed for each path.
 */
@Singleton
@Getter
public class CatsGlobalContext {
    public static final String HTTP_METHOD = "httpMethod";
    public static final String CONTRACT_PATH = "contractPath";
    public static final String ORIGINAL = "Original";
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(CatsGlobalContext.class);
    private final Map<String, Schema> schemaMap = new HashMap<>();
    private final Map<String, Example> exampleMap = new HashMap<>();
    private final Map<String, Map<String, Object>> additionalProperties = new HashMap<>();
    private final Set<Discriminator> discriminators = new HashSet<>();
    private final Map<String, Set<Object>> discriminatorValues = new HashMap<>();
    private final Map<String, Deque<String>> postSuccessfulResponses = new HashMap<>();
    private final Set<String> successfulDeletes = new HashSet<>();
    private final Properties fuzzersConfiguration = new Properties();
    private final Map<String, List<String>> generatedExamplesCache = new HashMap<>();
    private final Set<ProcessingError> recordedErrors = new HashSet<>();
    private final Set<String> errorLeaksKeywords = new HashSet<>();
    private final Set<String> refs = new HashSet<>();

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

    /**
     * Initializes the global context with the OpenAPI document, content types, fuzzers configuration and Cats configuration.
     *
     * @param openAPI              the OpenAPI document
     * @param contentType          the content types
     * @param fuzzersConfiguration the fuzzers configuration
     * @param catsConfiguration    the Cats configuration
     */
    public void init(OpenAPI openAPI, List<String> contentType, Properties fuzzersConfiguration, CatsConfiguration catsConfiguration,
                     Set<String> errorLeaksKeywords, Set<String> refs) {
        Map<String, Schema> allSchemasFromOpenApi = OpenApiUtils.getSchemas(openAPI, contentType);

        this.getSchemaMap().putAll(allSchemasFromOpenApi);
        this.getSchemaMap().put(NoMediaType.EMPTY_BODY, NoMediaType.EMPTY_BODY_SCHEMA);
        this.getExampleMap().putAll(OpenApiUtils.getExamples(openAPI));
        this.getFuzzersConfiguration().putAll(fuzzersConfiguration);

        //sometimes OpenAPI generator adds a "" entry
        this.getSchemaMap().remove("");
        this.catsConfiguration = catsConfiguration;
        this.openAPI = openAPI;
        this.errorLeaksKeywords.addAll(errorLeaksKeywords.stream().map(String::toLowerCase).collect(Collectors.toSet()));
        this.refs.addAll(refs);
    }

    /**
     * Checks if a property is a discriminator.
     *
     * @param propertyName the name of the property to check
     * @return true if the property is a discriminator, false otherwise
     */
    public boolean isDiscriminator(String propertyName) {
        return this.discriminatorValues.containsKey(propertyName);
    }

    /**
     * Records a discriminator in the global context.
     *
     * @param currentProperty the current property being processed
     * @param discriminator   the discriminator to record
     * @param examples        the examples for the discriminator property
     */
    public void recordDiscriminator(String currentProperty, Discriminator discriminator, List<Object> examples) {
        String discriminatorKey = (StringUtils.isBlank(currentProperty) ? "" : currentProperty + "#") + discriminator.getPropertyName();
        Set<Object> discriminatorValuesSet = this.discriminatorValues.computeIfAbsent(discriminatorKey, k -> new HashSet<>());
        discriminators.add(discriminator);
        if (CatsUtil.isNotEmpty(examples)) {
            discriminatorValuesSet.addAll(examples);
        } else {
            logger.warn("No examples found for discriminator property {}", discriminator.getPropertyName());
        }
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
        logger.trace("Getting schema from reference {}", reference);
        if (reference == null) {
            return null;
        }
        Object resolvedObject = getSchemaFromSimpleReferenceName(reference);

        if (reference.startsWith("#/components") || reference.startsWith("#/definitions")) {
            resolvedObject = getSchemaFromComponentsDefinitions(reference);
        }

        if (reference.startsWith("#/components") && resolvedObject == null) {
            resolvedObject = getObjectFromPathsReference(reference);
        }

        if (reference.contains("#/paths")) {
            String shortRef = reference.substring(reference.lastIndexOf("#/paths"));
            resolvedObject = getObjectFromPathsReference(shortRef);
        }

        Schema<?> result;
        if (resolvedObject instanceof Parameter parameter) {
            result = parameter.getSchema();
        } else {
            result = (Schema<?>) resolvedObject;
        }

        if (result != null && result.get$ref() != null && !result.get$ref().endsWith(NoMediaType.EMPTY_BODY) && !result.get$ref().equals(reference)) {
            result = getSchemaFromReference(result.get$ref());
        }

        this.schemaMap.putIfAbsent(reference, result);
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


    /**
     * Records an error in the global context.
     *
     * @param error the error to record
     */
    public void recordError(String error) {
        String contractPath = MDC.get(CONTRACT_PATH);
        String httpMethod = MDC.get(HTTP_METHOD);

        ProcessingError processingError = new ProcessingError(contractPath, httpMethod, error);
        this.recordedErrors.add(processingError);
    }

    /**
     * Prints the recorded errors if present.
     */
    public void writeRecordedErrorsIfPresent() {
        PrettyLogger consoleLogger = PrettyLoggerFactory.getConsoleLogger();
        if (recordedErrors.isEmpty()) {
            return;
        }
        consoleLogger.noFormat(ansi().bold().fgRed().a("\nThere were errors during fuzzers execution. It's recommended to fix them in order for all fuzzers to properly run: ").reset().toString());
        recordedErrors.forEach(error -> consoleLogger.noFormat("  -> " + error));
    }

    /**
     * Gets an object from the global context by reference.
     *
     * @param reference the reference to get
     * @return the object if found, null otherwise
     */
    public Object getObjectFromPathsReference(String reference) {
        logger.trace("Getting object from reference {}", reference);
        String jsonPointer = reference.substring(2);
        String[] parts = jsonPointer.split("/", -1);

        Object resolvedDefinition = openAPI;

        for (String part : parts) {
            part = URLDecoder.decode(part.replace("~1", "/"), StandardCharsets.UTF_8);
            String finalPart = part;
            resolvedDefinition = switch (resolvedDefinition) {
                case Map<?, ?> map -> map.get(finalPart);
                case OpenAPI _ when finalPart.equals("paths") -> openAPI.getPaths();
                case PathItem item when finalPart.equalsIgnoreCase("parameters") -> extractParameters(item);
                case PathItem item ->
                        item.readOperationsMap().get(PathItem.HttpMethod.valueOf(finalPart.toUpperCase(Locale.ROOT)));
                case Operation operation -> extractFromOperation(finalPart, operation, resolvedDefinition);
                case RequestBody requestBody when finalPart.equals("content") -> requestBody.getContent();
                case ApiResponse apiResponse -> extractFromApiResponse(finalPart, apiResponse, resolvedDefinition);
                case MediaType mediaType when "schema".equals(finalPart) -> mediaType.getSchema();
                case MediaType mediaType when "examples".equals(finalPart) -> mediaType.getExamples();
                case Schema<?> schema when "example".equals(finalPart) -> schema.getExample();
                case Schema<?> schema when "examples".equals(finalPart) -> schema.getExamples();
                case Schema<?> schema -> extractFromSchema(finalPart, schema, resolvedDefinition);
                case List<?> asList -> asList.get(Integer.parseInt(finalPart));
                case Object _ when "components".equals(finalPart) -> openAPI.getComponents();
                case Components components when "schemas".equals(finalPart) -> components.getSchemas();
                case Components components when "parameters".equalsIgnoreCase(finalPart) -> components.getParameters();
                case Components components when "headers".equalsIgnoreCase(finalPart) -> components.getHeaders();
                case Components components when "requestBodies".equalsIgnoreCase(finalPart) ->
                        components.getRequestBodies();
                case Components components when "examples".equalsIgnoreCase(finalPart) -> components.getExamples();
                case Example example -> example.getValue();
                case ObjectNode node -> node.get(finalPart);
                case ArrayNode arrayNode -> arrayNode.get(Integer.parseInt(finalPart));
                default -> null;
            };

            if (resolvedDefinition == null) break;
        }

        return resolvedDefinition;
    }

    private static List<Parameter> extractParameters(PathItem item) {
        List<Parameter> parameters = item.getParameters();
        if (parameters == null) {
            return item.readOperations().getFirst().getParameters();
        }
        return parameters;
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


    public void recordPathAndMethod(String path, HttpMethod method) {
        MDC.put(CONTRACT_PATH, path);
        MDC.put(HTTP_METHOD, method.toString());
    }
}
