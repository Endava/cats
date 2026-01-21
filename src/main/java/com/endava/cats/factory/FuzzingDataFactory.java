package com.endava.cats.factory;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.NoMediaType;
import com.endava.cats.openapi.OpenAPIModelGeneratorV2;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.OpenApiUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.endava.cats.openapi.OpenAPIModelGeneratorV2.SYNTH_SCHEMA_NAME;

/**
 * This class is responsible for creating {@link com.endava.cats.model.FuzzingData} objects based on the supplied OpenApi paths
 */
@ApplicationScoped
public class FuzzingDataFactory {
    private static final int RESPONSES_ARRAY_SIZE = 1;
    private static final int REQUEST_ARRAY_SIZE = 2;

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(FuzzingDataFactory.class);
    private final FilesArguments filesArguments;
    private final ProcessingArguments processingArguments;
    private final CatsGlobalContext globalContext;
    private final ValidDataFormat validDataFormat;
    private final FilterArguments filterArguments;

    /**
     * Constructs a new {@code FuzzingDataFactory} with the specified arguments.
     *
     * @param filesArguments      the arguments related to files and paths
     * @param processingArguments the arguments related to data processing
     * @param catsGlobalContext   the global context for Cats
     * @param validDataFormat     the valid data format for fuzzing
     * @param filterArguments     the arguments for filtering data
     */
    @Inject
    public FuzzingDataFactory(FilesArguments filesArguments, ProcessingArguments processingArguments, CatsGlobalContext catsGlobalContext, ValidDataFormat validDataFormat, FilterArguments filterArguments) {
        this.filesArguments = filesArguments;
        this.processingArguments = processingArguments;
        this.globalContext = catsGlobalContext;
        this.validDataFormat = validDataFormat;
        this.filterArguments = filterArguments;
    }

    /**
     * Creates a list of FuzzingData objects that will be used to fuzz the provided PathItems. The reason there is more than one FuzzingData object is due
     * to cases when the contract uses OneOf or AnyOf composite objects which causes the payload to have more than one variation.
     *
     * @param path    the path from the contract
     * @param item    the PathItem containing the details about the interaction with the path
     * @param openAPI the OpenAPI object
     * @return a list of FuzzingData items representing a template that will be used to apply the Fuzzers on
     */
    public List<FuzzingData> fromPathItem(String path, PathItem item, OpenAPI openAPI) {
        if (item.get$ref() != null) {
            item = globalContext.getPathItemFromReference(item.get$ref());
        }
        List<FuzzingData> fuzzingDataList = new ArrayList<>();
        if (item.getPost() != null) {
            logger.debug("Identified POST method for path {}", path);
            fuzzingDataList.addAll(this.getFuzzDataForPost(path, item, item.getPost(), openAPI));
        }

        if (item.getPut() != null) {
            logger.debug("Identified PUT method for path {}", path);
            fuzzingDataList.addAll(this.getFuzzDataForPut(path, item, item.getPut(), openAPI));
        }

        if (item.getPatch() != null) {
            logger.debug("Identified PATCH method for path {}", path);
            fuzzingDataList.addAll(this.getFuzzDataForPatch(path, item, item.getPatch(), openAPI));
        }

        if (item.getGet() != null) {
            logger.debug("Identified GET method for path {}", path);
            fuzzingDataList.addAll(this.getFuzzingDataForGet(path, item, item.getGet(), openAPI));
        }

        if (item.getDelete() != null) {
            logger.debug("Identified DELETE method for path {}", path);
            fuzzingDataList.addAll(this.getFuzzingDataForDelete(path, item, item.getDelete(), openAPI));
        }

        return fuzzingDataList;
    }


    /**
     * We filter the query parameters out of the synthetic schema created for the GET requests.
     *
     * @param schema the current ObjectSchema
     * @return a Set with all the query parameters
     */
    private Set<String> extractQueryParams(Schema<?> schema) {
        if (schema.getProperties() == null) {
            return Collections.emptySet();
        }
        return schema.getProperties().entrySet().stream().filter(entry -> entry.getValue().getName().endsWith("query")).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /**
     * In order to increase re-usability we will create a synthetic Schema object that will resemble the Schemas used by POST requests.
     * This will allow us to use the same code to fuzz the requests.
     * We will also do another "hack" so that required Parameters are also marked as required in their corresponding schema
     * <p>
     * Parameters supplied via the `urlParams` argument are not fuzzed.
     *
     * @param operationParameters the parameters defined in the OpenAPI contract
     * @return a Schema associated with the GET request which resembles the model for POST
     */
    private Schema<?> createSyntheticSchemaForGet(List<Parameter> operationParameters) {
        Schema<?> syntheticSchema = CatsModelUtils.newObjectSchema();
        syntheticSchema.setProperties(new LinkedHashMap<>());
        List<String> required = new ArrayList<>();

        List<Parameter> resolvedParameters = this.getResolvedParameters(operationParameters);

        for (Parameter parameter : resolvedParameters) {
            boolean isPathParam = "path".equalsIgnoreCase(parameter.getIn());
            boolean isQueryParam = "query".equalsIgnoreCase(parameter.getIn());

            if ((isPathParam || isQueryParam) && filesArguments.isNotUrlParam(parameter.getName()) && StringUtils.isNotBlank(parameter.getName())) {
                String newParameterName = parameter.getName() + "|" + parameter.getIn();
                parameter.setSchema(Optional.ofNullable(parameter.getSchema()).orElse(new Schema<>()));
                parameter.getSchema().setName(newParameterName);

                syntheticSchema.addProperty(parameter.getName(), parameter.getSchema());
                if (parameter.getSchema().getExample() == null) {
                    parameter.getSchema().setExample(parameter.getExample());
                }
                if (parameter.getRequired() != null && parameter.getRequired()) {
                    required.add(parameter.getName());
                }
            }
        }
        syntheticSchema.setRequired(required);
        return syntheticSchema;
    }

    List<Parameter> getResolvedParameters(List<Parameter> operationParameters) {
        return Optional.ofNullable(operationParameters)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(parameter -> {
                    if (parameter.get$ref() != null) {
                        Parameter param = (Parameter) globalContext.getObjectFromPathsReference(parameter.get$ref());
                        if (param == null) {
                            globalContext.recordError("Parameter definition for %s could not be resolved. It's either missing or is incorrect".formatted(parameter.get$ref()));
                        }
                        return param;
                    }
                    return parameter;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<FuzzingData> getFuzzDataForPost(String path, PathItem item, Operation operation, OpenAPI openAPI) {
        return this.getFuzzDataForHttpMethod(path, item, operation, HttpMethod.POST, openAPI);
    }

    private List<FuzzingData> getFuzzDataForPut(String path, PathItem item, Operation operation, OpenAPI openAPI) {
        return this.getFuzzDataForHttpMethod(path, item, operation, HttpMethod.PUT, openAPI);
    }


    private List<FuzzingData> getFuzzDataForPatch(String path, PathItem item, Operation operation, OpenAPI openAPI) {
        return this.getFuzzDataForHttpMethod(path, item, operation, HttpMethod.PATCH, openAPI);
    }

    private List<FuzzingData> getFuzzingDataForDelete(String path, PathItem item, Operation operation, OpenAPI openAPI) {
        return getFuzzDataForNonBodyMethods(path, item, operation, openAPI, HttpMethod.DELETE);
    }

    private List<FuzzingData> getFuzzingDataForGet(String path, PathItem item, Operation operation, OpenAPI openAPI) {
        return getFuzzDataForNonBodyMethods(path, item, operation, openAPI, HttpMethod.GET);
    }

    /**
     * The reason we get more than one {@code FuzzingData} objects is related to the usage of {@code anyOf, oneOf or allOf} elements inside the contract definition.
     * The method will compute all the possible combinations so that it covers all payload definitions.
     *
     * @param path      the current path
     * @param item      the current PathItem
     * @param operation the current OpenAPI Operation
     * @param method    the current HTTP method
     * @return a list  of FuzzingData used to Fuzz
     */
    private List<FuzzingData> getFuzzDataForHttpMethod(String path, PathItem item, Operation operation, HttpMethod method, OpenAPI openAPI) {
        globalContext.recordPathAndMethod(path, method);
        if (this.isDeprecated(operation)) {
            logger.info("Operation {} {} is deprecated and --skipDeprecatedOperations is on", path, method);
            return Collections.emptyList();
        }
        if (this.isNotIncludedTag(operation)) {
            logger.info("Operation {} {} will be skipped as tag is not included", path, method);
            return Collections.emptyList();
        }
        if (this.isNotIncludedOperationId(operation)) {
            logger.info("Operation {} {} will be skipped as operationId [{}] is not included", path, method, operation.getOperationId());
            return Collections.emptyList();
        }

        List<FuzzingData> fuzzingDataList = new ArrayList<>();
        MediaType mediaType = this.getMediaType(operation, openAPI);

        if (mediaType == null) {
            logger.warn("Content type not supported for path {}, method {}. CATS detects application/json by default. " + "You might try to supply the custom content type using --contentType argument", path, method);
            return Collections.emptyList();
        }
        List<String> reqSchemaNames = this.getCurrentRequestSchemaName(mediaType);
        logger.debug("Request schema names identified for path {}, method {}: {}", path, method, reqSchemaNames);

        KeyValuePair<String, Schema<?>> paramsSchema = this.createSyntheticSchemaForGet(operation);
        //we need this in order to be able to generate path params if not supplied by the user
        String pathParamsExample = this.getRequestPayloadsSamples(null, paramsSchema.getKey()).examplePayloads().getFirst();
        Set<String> queryParams = this.extractQueryParams(paramsSchema.getValue());
        logger.debug("Query params for path {}, method {}: {}", path, method, queryParams);

        Set<Object> examples = this.extractExamples(mediaType);
        Map<String, List<String>> responses = this.getResponsePayloads(operation);
        Map<String, List<String>> responsesContentTypes = this.getResponseContentTypes(operation);
        List<String> requestContentTypes = this.getRequestContentTypes(operation, openAPI);
        Map<String, Set<String>> responseHeaders = this.getResponseHeaders(operation);
        logger.debug("Request content types for path {}, method {}: {}", path, method, requestContentTypes);

        for (String reqSchemaName : reqSchemaNames) {
            GenerationResult generationResult = this.getRequestPayloadsSamples(mediaType, reqSchemaName);
            fuzzingDataList.addAll(generationResult.examplePayloads().stream()
                    .map(payload -> FuzzingData.builder()
                            .method(method).path(path)
                            .contractPath(path)
                            .headers(this.extractHeaders(operation))
                            .payload(payload)
                            .responseCodes(operation.getResponses().keySet())
                            .reqSchema(globalContext.getSchemaFromReference(reqSchemaName))
                            .pathItem(item).responseContentTypes(responsesContentTypes)
                            .requestContentTypes(requestContentTypes)
                            .isRequestBodyRequired(this.isRequestBodyRequired(operation))
                            .schemaMap(globalContext.getSchemaMap())
                            .responses(responses)
                            .requestPropertyTypes(generationResult.requestDataTypes())
                            .openApi(openAPI)
                            .tags(operation.getTags())
                            .reqSchemaName(reqSchemaName)
                            .examples(examples)
                            .selfReferenceDepth(processingArguments.getSelfReferenceDepth())
                            .limitNumberOfFields(processingArguments.getLimitNumberOfFields())
                            .includeFieldTypes(filterArguments.getFieldTypes())
                            .skipFieldTypes(filterArguments.getSkipFieldTypes())
                            .includeFieldFormats(filterArguments.getFieldFormats())
                            .skipFieldFormats(filterArguments.getSkipFieldFormats())
                            .skippedFieldsForAllFuzzers(filterArguments.getSkipFieldsToBeSkippedForAllFuzzers())
                            .skippedFuzzersForPath(filterArguments.getFuzzersToSkipForOperationExtensions(operation.getExtensions()))
                            .responseHeaders(responseHeaders)
                            .pathParamsPayload(pathParamsExample)
                            .queryParams(queryParams)
                            .build()).toList());
        }

        return List.copyOf(fuzzingDataList);
    }

    private boolean isRequestBodyRequired(Operation operation) {
        return operation.getRequestBody() != null && Boolean.TRUE.equals(operation.getRequestBody().getRequired());
    }

    private boolean isDeprecated(Operation operation) {
        return operation.getDeprecated() != null && operation.getDeprecated() && filterArguments.isSkipDeprecated();
    }

    private boolean isNotIncludedTag(Operation operation) {
        boolean isNotIncluded = !filterArguments.getTags().isEmpty() &&
                (operation.getTags() == null || operation.getTags().stream().noneMatch(tag -> filterArguments.getTags().contains(tag)));

        boolean isSkipped = !filterArguments.getSkippedTags().isEmpty() && operation.getTags() != null &&
                operation.getTags()
                        .stream()
                        .anyMatch(tag -> filterArguments.getSkippedTags().contains(tag));

        return isNotIncluded || isSkipped;
    }

    private boolean isNotIncludedOperationId(Operation operation) {
        String operationId = operation.getOperationId();

        boolean isNotIncluded = !filterArguments.getOperationIds().isEmpty() &&
                (operationId == null || filterArguments.getOperationIds().stream()
                        .noneMatch(operationId::equalsIgnoreCase));

        boolean isSkipped = !filterArguments.getSkipOperationIds().isEmpty() && operationId != null &&
                filterArguments.getSkipOperationIds().stream()
                        .anyMatch(operationId::equalsIgnoreCase);

        return isNotIncluded || isSkipped;
    }

    /**
     * A similar FuzzingData object will be created for GET or DELETE requests. The "payload" will be a JSON with all the query or path params.
     * In order to achieve this a synthetic object is created that will act as a root object holding all the query or path params as child schemas.
     * The method returns a list of FuzzingData as you might have oneOf operations which will create multiple payloads.
     *
     * @param path      the current path
     * @param item      the current path item
     * @param openAPI   the full OpenAPI object
     * @param operation the OpenApi operation
     * @return a list of FuzzingData objects
     */
    private List<FuzzingData> getFuzzDataForNonBodyMethods(String path, PathItem item, Operation operation, OpenAPI openAPI, HttpMethod method) {
        globalContext.recordPathAndMethod(path, method);
        if (this.isDeprecated(operation)) {
            logger.info("Operation {} {} is deprecated and --skipDeprecatedOperations is on; skipping", path, method);
            return Collections.emptyList();
        }
        if (this.isNotIncludedTag(operation)) {
            logger.info("Operation {} {} will be skipped as tag is not included", path, method);
            return Collections.emptyList();
        }
        if (this.isNotIncludedOperationId(operation)) {
            logger.info("Operation {} {} will be skipped as operationId [{}] is not included", path, method, operation.getOperationId());
            return Collections.emptyList();
        }

        KeyValuePair<String, Schema<?>> syntheticSchema = createSyntheticSchemaForGet(operation);
        Set<String> queryParams = this.extractQueryParams(syntheticSchema.getValue());
        logger.debug("Query params for path {}, method {}: {}", path, method, queryParams);

        GenerationResult generationResult = this.getRequestPayloadsSamples(null, syntheticSchema.getKey());
        Map<String, List<String>> responsesContentTypes = this.getResponseContentTypes(operation);
        Map<String, List<String>> responses = this.getResponsePayloads(operation);
        List<String> requestContentTypes = this.getRequestContentTypes(operation, openAPI);
        Map<String, Set<String>> responseHeaders = this.getResponseHeaders(operation);

        logger.debug("Request content types for path {}, method {}: {}", path, method, requestContentTypes);

        return generationResult.examplePayloads().stream()
                .map(payload -> FuzzingData.builder()
                        .method(method).path(path)
                        .contractPath(path)
                        .headers(this.extractHeaders(operation))
                        .payload(payload)
                        .responseCodes(operation.getResponses().keySet())
                        .reqSchema(syntheticSchema.getValue())
                        .pathItem(item)
                        .schemaMap(globalContext.getSchemaMap())
                        .responses(responses)
                        .responseContentTypes(responsesContentTypes)
                        .requestPropertyTypes(generationResult.requestDataTypes())
                        .requestContentTypes(requestContentTypes)
                        .queryParams(queryParams)
                        .openApi(openAPI)
                        .tags(operation.getTags())
                        .reqSchemaName(SYNTH_SCHEMA_NAME)
                        .selfReferenceDepth(processingArguments.getSelfReferenceDepth())
                        .limitNumberOfFields(processingArguments.getLimitNumberOfFields())
                        .includeFieldTypes(filterArguments.getFieldTypes())
                        .skipFieldTypes(filterArguments.getSkipFieldTypes())
                        .includeFieldFormats(filterArguments.getFieldFormats())
                        .skipFieldFormats(filterArguments.getSkipFieldFormats())
                        .skippedFieldsForAllFuzzers(filterArguments.getSkipFieldsToBeSkippedForAllFuzzers())
                        .skippedFuzzersForPath(filterArguments.getFuzzersToSkipForOperationExtensions(operation.getExtensions()))
                        .responseHeaders(responseHeaders)
                        .build())
                .toList();
    }

    private KeyValuePair<String, Schema<?>> createSyntheticSchemaForGet(Operation operation) {
        Schema<?> syntheticSchema = this.createSyntheticSchemaForGet(operation.getParameters());
        globalContext.getSchemaMap().put(SYNTH_SCHEMA_NAME + operation.getOperationId(), syntheticSchema);

        return new KeyValuePair<>(SYNTH_SCHEMA_NAME + operation.getOperationId(), syntheticSchema);
    }

    Set<Object> extractExamples(MediaType mediaType) {
        Set<Object> examples = new HashSet<>();
        if (mediaType == null) {
            return examples;
        }
        examples.add(Optional.ofNullable(mediaType.getExample()).orElse(""));
        if (mediaType.getExamples() != null) {
            examples.addAll(mediaType.getExamples().values()
                    .stream()
                    .map(Example::getValue)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));

            examples.addAll(mediaType.getExamples().values()
                    .stream()
                    .filter(example -> example.get$ref() != null)
                    .map(example -> {
                        Example exampleFromSchemaMap = globalContext.getExampleMap().get(CatsModelUtils.getSimpleRef(example.get$ref()));

                        if (exampleFromSchemaMap == null && example.get$ref().contains("/value/")) {
                            //this might be a multi-level example something like: #/components/examples/JSON_WORKER_EXAMPLES/value/WORKER_COMPENSATION_PAYRATE_POST_PATCH

                            String[] exampleKeys = example.get$ref().replace("#/components/examples/", "")
                                    .replace("value/", "")
                                    .split("/", -1);
                            return ((ObjectNode) globalContext.getExampleMap().get(exampleKeys[0]).getValue()).get(exampleKeys[1]).get("value").toString();
                        }

                        if (exampleFromSchemaMap == null) {
                            exampleFromSchemaMap = (Example) globalContext.getObjectFromPathsReference(example.get$ref());
                        }

                        return exampleFromSchemaMap != null ? exampleFromSchemaMap.getValue() : null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
        }
        examples.remove("");
        return examples;
    }


    /**
     * We get the definition name for each request type. This also includes cases when we have AnyOf or OneOf schemas.
     *
     * @param mediaType the media type extracted from the Operation
     * @return a list of request scheme names from the current media type
     */
    private List<String> getCurrentRequestSchemaName(MediaType mediaType) {
        List<String> reqSchemas = new ArrayList<>();
        if (mediaType.getSchema() != null) {
            String currentRequestSchema = mediaType.getSchema().get$ref();

            if (currentRequestSchema == null && CatsModelUtils.isArraySchema(mediaType.getSchema())) {
                currentRequestSchema = mediaType.getSchema().getItems().get$ref();
            }
            if (currentRequestSchema != null) {
                reqSchemas.add(CatsModelUtils.getSimpleRef(currentRequestSchema));
            } else if (CatsModelUtils.isComposedSchema(mediaType.getSchema())) {
                Schema<?> composedSchema = mediaType.getSchema();
                if (composedSchema.getAnyOf() != null) {
                    composedSchema.getAnyOf().forEach(innerSchema -> reqSchemas.add(calculateSchemaRef(innerSchema)));
                }
                if (composedSchema.getOneOf() != null) {
                    composedSchema.getOneOf().forEach(innerSchema -> reqSchemas.add(calculateSchemaRef(innerSchema)));
                }
            } else {
                String refForSchema = SYNTH_SCHEMA_NAME + CatsRandom.alphabetic(5);
                reqSchemas.add(refForSchema);
                globalContext.putSchemaReference(refForSchema, mediaType.getSchema());
            }
        }
        return reqSchemas;
    }

    private String calculateSchemaRef(Schema innerSchema) {
        if (innerSchema.get$ref() == null) {
            String refForSchema = SYNTH_SCHEMA_NAME + CatsRandom.alphabetic(5);
            globalContext.putSchemaReference(refForSchema, innerSchema);
            return refForSchema;
        }
        return CatsModelUtils.getSimpleRef(innerSchema.get$ref());
    }

    private MediaType getMediaType(Operation operation, OpenAPI openAPI) {
        for (String contentType : processingArguments.getContentType()) {
            if (operation.getRequestBody() != null && operation.getRequestBody().get$ref() != null) {
                String reqBodyRef = operation.getRequestBody().get$ref();

                RequestBody requestBody = openAPI.getComponents().getRequestBodies().get(CatsModelUtils.getSimpleRef(reqBodyRef));
                if (requestBody.get$ref() != null) {
                    requestBody = (RequestBody) globalContext.getObjectFromPathsReference(requestBody.get$ref());
                }
                return OpenApiUtils.getMediaTypeFromContent(requestBody.getContent(), contentType);
            } else if (operation.getRequestBody() != null && OpenApiUtils.hasContentType(operation.getRequestBody().getContent(), List.of(contentType))) {
                return OpenApiUtils.getMediaTypeFromContent(operation.getRequestBody().getContent(), contentType);
            }
        }
        return hasContent(operation) ? operation.getRequestBody().getContent().get("*/*") : new NoMediaType();
    }

    static boolean hasContent(Operation operation) {
        return operation.getRequestBody() != null && operation.getRequestBody().getContent() != null;
    }

    private GenerationResult getRequestPayloadsSamples(MediaType mediaType, String reqSchemaName) {
        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(globalContext, validDataFormat, processingArguments.examplesFlags(),
                processingArguments.getSelfReferenceDepth(), processingArguments.isUseDefaults(), REQUEST_ARRAY_SIZE);

        /* Event though the media type might have an example set, we still generate samples in order to properly map each field with its corresponding data type*/
        List<String> result = this.generateSample(reqSchemaName, generator);
        if (mediaType != null && CatsModelUtils.isArraySchema(mediaType.getSchema())) {
            /*when dealing with ArraySchemas we make sure we have 2 elements in the array*/
            result = result.stream()
                    .map(payload -> {
                        if (JsonUtils.isJsonArray(payload)) {
                            return payload;
                        }
                        return "[" + payload + "," + payload + "]";
                    }).toList();
        }

        if (mediaType != null && processingArguments.isUseRequestBodyExamples()) {
            List<String> examples = extractExamples(mediaType).stream()
                    .map(JsonUtils::serialize)
                    .filter(Objects::nonNull)
                    .toList();
            if (!examples.isEmpty()) {
                result = new ArrayList<>(examples);
            }
        }

        return new GenerationResult(result, generator.getRequestDataTypes());
    }

    private List<String> generateSample(String reqSchemaName, OpenAPIModelGeneratorV2 generator) {
        String onlySchemaName = CatsModelUtils.getSimpleRef(reqSchemaName);
        if (globalContext.isExampleAlreadyGenerated(onlySchemaName) && processingArguments.isCachePayloads()) {
            logger.debug("Example for schema name {} already generated, using cached value", onlySchemaName);
            return globalContext.getGeneratedExamplesCache().get(onlySchemaName);
        }

        long t0 = System.currentTimeMillis();
        logger.debug("Starting to generate example for schema name {}", reqSchemaName);
        List<String> examples = generator.generate(reqSchemaName);
        logger.debug("Finish generating example for schema name {}, took {}ms", reqSchemaName, (System.currentTimeMillis() - t0));

        if (processingArguments.getLimitXxxOfCombinations() > 0) {
            int maxCombinations = Math.min(processingArguments.getLimitXxxOfCombinations(), examples.size());
            return examples.stream()
                    .limit(maxCombinations)
                    .toList();
        }
        logger.debug("Cached example for schema name {}", reqSchemaName);
        globalContext.addGeneratedExample(reqSchemaName, examples);
        return examples;
    }

    private List<String> getRequestContentTypes(Operation operation, OpenAPI openAPI) {
        List<String> requests = new ArrayList<>();
        if (operation.getRequestBody() != null) {
            Content defaultContent = buildDefaultContent();
            String reqBodyRef = operation.getRequestBody().get$ref();
            if (reqBodyRef != null) {
                operation.getRequestBody().setContent(openAPI.getComponents().getRequestBodies().get(CatsModelUtils.getSimpleRef(reqBodyRef)).getContent());
            }
            requests.addAll(new ArrayList<>(Optional.ofNullable(operation.getRequestBody().getContent()).orElse(defaultContent).keySet()));
        } else {
            requests.add(processingArguments.getDefaultContentType());
        }
        return requests;
    }

    private Content buildDefaultContent() {
        Content defaultContent = new Content();
        defaultContent.addMediaType(processingArguments.getDefaultContentType(), new MediaType().schema(new Schema<>()));
        return defaultContent;
    }

    private Map<String, Set<String>> getResponseHeaders(Operation operation) {
        Map<String, Set<String>> responses = new HashMap<>();
        for (String responseCode : operation.getResponses().keySet()) {
            ApiResponse apiResponse = operation.getResponses().get(responseCode);
            if (apiResponse.get$ref() != null) {
                Object potentialApiResponse = globalContext.getApiResponseFromReference(apiResponse.get$ref());
                if (potentialApiResponse instanceof ApiResponse apiResponseCasted) {
                    apiResponse = apiResponseCasted;
                }
            }

            responses.put(responseCode, Optional.ofNullable(apiResponse.getHeaders()).orElse(Collections.emptyMap())
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().getDeprecated() == null || !entry.getValue().getDeprecated())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet()));
        }

        return responses;
    }


    private Map<String, List<String>> getResponseContentTypes(Operation operation) {
        Map<String, List<String>> responses = new HashMap<>();
        for (String responseCode : operation.getResponses().keySet()) {
            ApiResponse apiResponse = operation.getResponses().get(responseCode);
            if (apiResponse.get$ref() != null) {
                Object potentialApiResponse = globalContext.getApiResponseFromReference(apiResponse.get$ref());
                if (potentialApiResponse instanceof ApiResponse apiResponseCasted) {
                    apiResponse = apiResponseCasted;
                }
            }
            Content content = apiResponse.getContent();
            if (content == null || content.isEmpty()) {
                content = buildDefaultContent();
            }
            responses.put(responseCode, new ArrayList<>(content.keySet()));
        }

        return responses;
    }

    /**
     * We need to get JSON structural samples for each response code documented into the contract. This includes ONE_OF or ANY_OF combinations.
     *
     * @param operation the current OpenAPI operation
     * @return a list if response payloads associated to each response code
     */
    private Map<String, List<String>> getResponsePayloads(Operation operation) {
        Map<String, List<String>> responses = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        OpenAPIModelGeneratorV2 generator = new OpenAPIModelGeneratorV2(globalContext, validDataFormat, processingArguments.examplesFlags(),
                processingArguments.getSelfReferenceDepth(), processingArguments.isUseDefaults(), RESPONSES_ARRAY_SIZE, processingArguments.isResolveXxxOfCombinationForResponses());

        for (String responseCode : operation.getResponses().keySet()) {
            List<String> openapiExamples = this.getExamplesFromApiResponseForResponseCode(operation, responseCode);
            if (!openapiExamples.isEmpty() && processingArguments.isUseResponseBodyExamples()) {
                responses.put(responseCode, openapiExamples);
                continue;
            }
            String responseSchemaRef = this.extractResponseSchemaRef(operation, responseCode);
            if (responseSchemaRef != null) {
                List<String> samples = this.generateSample(responseSchemaRef, generator);
                responses.put(responseCode, samples);
            } else {
                responses.put(responseCode, Collections.emptyList());
            }
        }
        return responses;
    }

    private List<String> getExamplesFromApiResponseForResponseCode(Operation operation, String responseCode) {
        ApiResponse apiResponse = operation.getResponses().get(responseCode);
        if (apiResponse.get$ref() != null) {
            Object potentialApiResponse = globalContext.getApiResponseFromReference(apiResponse.get$ref());
            if (potentialApiResponse instanceof ApiResponse apiResponseCasted) {
                apiResponse = apiResponseCasted;
            }
        }
        if (apiResponse.getContent() != null) {
            Set<Object> examples = extractExamples(apiResponse.getContent().get(processingArguments.getDefaultContentType()));
            return examples.stream()
                    .map(JsonUtils::serialize)
                    .toList();
        }
        return Collections.emptyList();
    }

    private String extractResponseSchemaRef(Operation operation, String responseCode) {
        for (String contentType : processingArguments.getContentType()) {
            ApiResponse apiResponse = operation.getResponses().get(responseCode);
            if (StringUtils.isNotEmpty(apiResponse.get$ref())) {
                logger.trace("Getting apiResponse from ref {}", apiResponse.get$ref());
                Object possibleApiResponseResolved = globalContext.getApiResponseFromReference(apiResponse.get$ref());
                if (possibleApiResponseResolved instanceof ApiResponse apiResponseCasted) {
                    apiResponse = apiResponseCasted;
                } else {
                    return apiResponse.get$ref();
                }
            }
            if (OpenApiUtils.hasContentType(apiResponse.getContent(), processingArguments.getContentType())) {
                MediaType mediaType = OpenApiUtils.getMediaTypeFromContent(apiResponse.getContent(), contentType);
                if (mediaType == null) {
                    continue;
                }

                Schema<?> respSchema = Optional.ofNullable(mediaType.getSchema()).orElse(new Schema<>());

                return extractSchemaRef(respSchema, operation, responseCode);
            }
        }
        return null;
    }

    private String extractSchemaRef(Schema<?> respSchema, Operation operation, String responseCode) {
        String refKey = Optional.ofNullable(operation.getOperationId()).orElse(CatsRandom.alphabetic(5)) + responseCode;
        String finalRef = refKey;

        if (CatsModelUtils.isArraySchema(respSchema)) {
            if (respSchema.getItems().get$ref() == null) {
                globalContext.putSchemaReference(refKey, respSchema);
            } else {
                finalRef = respSchema.getItems().get$ref();
            }
        } else {
            if (respSchema.get$ref() == null) {
                globalContext.putSchemaReference(refKey, respSchema);
            } else {
                finalRef = respSchema.get$ref();
            }
        }
        return finalRef;
    }


    private Set<CatsHeader> extractHeaders(Operation operation) {
        Set<CatsHeader> headers = new HashSet<>();
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                if ("header".equalsIgnoreCase(param.getIn())) {
                    this.inlineSchemaIfNeeded(param);
                    try {
                        headers.add(CatsHeader.fromHeaderParameter(param));
                    } catch (IllegalArgumentException _) {
                        globalContext.recordError("A valid string could not be generated for the header '" + param.getName() + "' using the pattern '" + param.getSchema().getPattern() + "'. Please consider either changing the pattern or simplifying it.");
                        headers.add(CatsHeader.from(param.getName(), OpenAPIModelGeneratorV2.DEFAULT_STRING_WHEN_GENERATION_FAILS, param.getRequired()));
                    }
                }
            }
        }

        return headers;
    }

    /**
     * If the parameter does not have a schema, we need to inline it.
     *
     * @param parameter the current parameter
     */
    private void inlineSchemaIfNeeded(Parameter parameter) {
        if (parameter.getSchema() != null || parameter.getContent() == null) {
            return;
        }

        Schema<?> schema = parameter.getContent().values().iterator().next().getSchema();

        parameter.setSchema(schema);

        List<String> examples = this.generateSample(schema.get$ref(), new OpenAPIModelGeneratorV2(globalContext, validDataFormat, processingArguments.examplesFlags(),
                processingArguments.getSelfReferenceDepth(), processingArguments.isUseDefaults(), REQUEST_ARRAY_SIZE));

        schema.setExample(examples.getFirst());
    }

    public record GenerationResult(List<String> examplePayloads, Map<String, Schema> requestDataTypes) {
    }
}
