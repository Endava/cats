package com.endava.cats.factory;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.generator.OpenAPIModelGenerator;
import com.endava.cats.openapi.OpenApiUtils;
import com.endava.cats.util.CatsModelUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static com.endava.cats.model.generator.OpenAPIModelGenerator.SYNTH_SCHEMA_NAME;

/**
 * This class is responsible for creating {@link com.endava.cats.model.FuzzingData} objects based on the supplied OpenApi paths
 */
@ApplicationScoped
public class FuzzingDataFactory {
    private static final String ANY_OF = "ANY_OF";
    private static final String ONE_OF = "ONE_OF";
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

        for (Parameter parameter : Optional.ofNullable(operationParameters).orElseGet(Collections::emptyList)) {
            boolean isPathParam = "path".equalsIgnoreCase(parameter.getIn());
            boolean isQueryParam = "query".equalsIgnoreCase(parameter.getIn());

            if ((isPathParam || isQueryParam) && filesArguments.isNotUrlParam(parameter.getName())) {
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
        if (this.isDeprecated(operation)) {
            logger.info("Operation {} {} is deprecated and --skipDeprecatedOperations is on", path, method);
            return Collections.emptyList();
        }
        if (this.isNotIncludedTag(operation)) {
            logger.info("Operation {} {} will be skipped as tag is not included", path, method);
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

        Set<String> examples = this.extractExamples(mediaType);

        Map<String, List<String>> responses = this.getResponsePayloads(operation);
        Map<String, List<String>> responsesContentTypes = this.getResponseContentTypes(operation);
        List<String> requestContentTypes = this.getRequestContentTypes(operation, openAPI);
        Map<String, Set<String>> responseHeaders = this.getResponseHeaders(operation);
        logger.debug("Request content types for path {}, method {}: {}", path, method, requestContentTypes);

        for (String reqSchemaName : reqSchemaNames) {
            List<String> payloadSamples = this.getRequestPayloadsSamples(mediaType, reqSchemaName);
            fuzzingDataList.addAll(payloadSamples.stream()
                    .map(payload -> FuzzingData.builder()
                            .method(method).path(path)
                            .contractPath(path)
                            .headers(this.extractHeaders(operation))
                            .payload(payload)
                            .responseCodes(operation.getResponses().keySet())
                            .reqSchema(globalContext.getSchemaMap().get(reqSchemaName))
                            .pathItem(item).responseContentTypes(responsesContentTypes)
                            .requestContentTypes(requestContentTypes)
                            .schemaMap(globalContext.getSchemaMap())
                            .responses(responses)
                            .requestPropertyTypes(globalContext.getRequestDataTypes())
                            .openApi(openAPI)
                            .tags(operation.getTags())
                            .reqSchemaName(reqSchemaName)
                            .examples(examples)
                            .selfReferenceDepth(processingArguments.getSelfReferenceDepth())
                            .includeFieldTypes(filterArguments.getFieldTypes())
                            .skipFieldTypes(filterArguments.getSkipFieldTypes())
                            .includeFieldFormats(filterArguments.getFieldFormats())
                            .skipFieldFormats(filterArguments.getSkipFieldFormats())
                            .skippedFieldsForAllFuzzers(filterArguments.getSkipFieldsToBeSkippedForAllFuzzers())
                            .responseHeaders(responseHeaders)
                            .build()).toList());
        }

        return List.copyOf(fuzzingDataList);
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
        if (this.isDeprecated(operation)) {
            logger.info("Operation {} {} is deprecated and --skipDeprecatedOperations is on; skipping", path, method);
            return Collections.emptyList();
        }
        if (this.isNotIncludedTag(operation)) {
            logger.info("Operation {} {} will be skipped as tag is not included", path, method);
            return Collections.emptyList();
        }

        Schema<?> syntheticSchema = this.createSyntheticSchemaForGet(operation.getParameters());

        globalContext.getSchemaMap().put(SYNTH_SCHEMA_NAME + operation.getOperationId(), syntheticSchema);
        Set<String> queryParams = this.extractQueryParams(syntheticSchema);
        logger.debug("Query params for path {}, method {}: {}", path, method, queryParams);

        List<String> payloadSamples = this.getRequestPayloadsSamples(null, SYNTH_SCHEMA_NAME + operation.getOperationId());
        Map<String, List<String>> responsesContentTypes = this.getResponseContentTypes(operation);
        Map<String, List<String>> responses = this.getResponsePayloads(operation);
        List<String> requestContentTypes = this.getRequestContentTypes(operation, openAPI);
        Map<String, Set<String>> responseHeaders = this.getResponseHeaders(operation);

        logger.debug("Request content types for path {}, method {}: {}", path, method, requestContentTypes);

        return payloadSamples.stream()
                .map(payload -> FuzzingData.builder()
                        .method(method).path(path)
                        .contractPath(path)
                        .headers(this.extractHeaders(operation))
                        .payload(payload)
                        .responseCodes(operation.getResponses().keySet())
                        .reqSchema(syntheticSchema)
                        .pathItem(item)
                        .schemaMap(globalContext.getSchemaMap())
                        .responses(responses)
                        .responseContentTypes(responsesContentTypes)
                        .requestPropertyTypes(globalContext.getRequestDataTypes())
                        .requestContentTypes(requestContentTypes)
                        .queryParams(queryParams)
                        .openApi(openAPI)
                        .tags(operation.getTags())
                        .reqSchemaName(SYNTH_SCHEMA_NAME)
                        .selfReferenceDepth(processingArguments.getSelfReferenceDepth())
                        .includeFieldTypes(filterArguments.getFieldTypes())
                        .skipFieldTypes(filterArguments.getSkipFieldTypes())
                        .includeFieldFormats(filterArguments.getFieldFormats())
                        .skipFieldFormats(filterArguments.getSkipFieldFormats())
                        .skippedFieldsForAllFuzzers(filterArguments.getSkipFieldsToBeSkippedForAllFuzzers())
                        .responseHeaders(responseHeaders)
                        .build())
                .toList();
    }

    private Set<String> extractExamples(MediaType mediaType) {
        Set<String> examples = new HashSet<>();
        examples.add(Optional.ofNullable(mediaType.getExample()).orElse("").toString());
        if (mediaType.getExamples() != null) {
            examples.addAll(mediaType.getExamples().values()
                    .stream()
                    .map(Example::getValue)
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toSet()));

            examples.addAll(mediaType.getExamples().values()
                    .stream()
                    .filter(example -> example.get$ref() != null)
                    .map(example -> {
                        Example exampleFromSchemaMap = globalContext.getExampleMap().get(this.getSchemaName(example.get$ref()));
                        if (exampleFromSchemaMap == null && example.get$ref().contains("/value/")) {
                            //this might be a multi-level example something like: #/components/examples/JSON_WORKER_EXAMPLES/value/WORKER_COMPENSATION_PAYRATE_POST_PATCH

                            String[] exampleKeys = example.get$ref().replace("#/components/examples/", "")
                                    .replace("value/", "")
                                    .split("/", -1);
                            return ((ObjectNode) globalContext.getExampleMap().get(exampleKeys[0]).getValue()).get(exampleKeys[1]).get("value").toString();
                        }
                        return exampleFromSchemaMap != null ? exampleFromSchemaMap.getValue() : null;
                    })
                    .filter(Objects::nonNull)
                    .map(Object::toString)
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
                reqSchemas.add(this.getSchemaName(currentRequestSchema));
            } else if (CatsModelUtils.isComposedSchema(mediaType.getSchema())) {
                Schema<?> composedSchema = mediaType.getSchema();
                if (composedSchema.getAnyOf() != null) {
                    composedSchema.getAnyOf().forEach(innerSchema -> reqSchemas.add(this.getSchemaName(innerSchema.get$ref())));
                }
                if (composedSchema.getOneOf() != null) {
                    composedSchema.getOneOf().forEach(innerSchema -> reqSchemas.add(this.getSchemaName(innerSchema.get$ref())));
                }
            }
        }
        return reqSchemas;
    }

    private String getSchemaName(String currentRequestSchema) {
        return currentRequestSchema.substring(Objects.requireNonNull(currentRequestSchema).lastIndexOf('/') + 1);
    }


    private MediaType getMediaType(Operation operation, OpenAPI openAPI) {
        for (String contentType : processingArguments.getContentType()) {
            if (operation.getRequestBody() != null && operation.getRequestBody().get$ref() != null) {
                String reqBodyRef = operation.getRequestBody().get$ref();
                return OpenApiUtils.getMediaTypeFromContent(openAPI.getComponents().getRequestBodies().get(this.getSchemaName(reqBodyRef)).getContent(), contentType);
            } else if (operation.getRequestBody() != null && OpenApiUtils.hasContentType(operation.getRequestBody().getContent(), List.of(contentType))) {
                return OpenApiUtils.getMediaTypeFromContent(operation.getRequestBody().getContent(), contentType);
            }
        }
        return operation.getRequestBody() != null ? operation.getRequestBody().getContent().get("*/*") : new NoMediaType();
    }

    private List<String> getRequestPayloadsSamples(MediaType mediaType, String reqSchemaName) {
        OpenAPIModelGenerator generator = new OpenAPIModelGenerator(globalContext, validDataFormat, processingArguments.isUseExamples(), processingArguments.getSelfReferenceDepth());
        List<String> result = this.generateSample(reqSchemaName, generator, true);

        if (mediaType != null && CatsModelUtils.isArraySchema(mediaType.getSchema())) {
            /*when dealing with ArraySchemas we make sure we have 2 elements in the array*/
            result = result.stream().map(payload -> "[" + payload + "," + payload + "]").toList();
        }
        return result;
    }

    private List<String> generateSample(String reqSchemaName, OpenAPIModelGenerator generator, boolean createXxxOfCombinations) {
        long t0 = System.currentTimeMillis();
        logger.debug("Starting to generate example for schema name {}", reqSchemaName);
        Map<String, String> examples = generator.generate(reqSchemaName);
        logger.debug("Finish generating example for schema name {}, took {}ms", reqSchemaName, (System.currentTimeMillis() - t0));

        if (examples.isEmpty()) {
            throw new IllegalArgumentException("Scheme is not declared: " + reqSchemaName);
        }
        String payloadSample = examples.get("example");

        payloadSample = this.squashAllOfElements(payloadSample);
        List<String> payloadCombinationsBasedOnOneOfAndAnyOf = List.of(payloadSample);

        if (createXxxOfCombinations) {
            payloadCombinationsBasedOnOneOfAndAnyOf = this.getPayloadCombinationsBasedOnOneOfAndAnyOf(payloadSample);
        }

        if (processingArguments.isFilterXxxFromRequestPayloads()) {
            payloadCombinationsBasedOnOneOfAndAnyOf = payloadCombinationsBasedOnOneOfAndAnyOf
                    .stream()
                    .filter(payload -> !(payload.contains("ANY_OF") || payload.contains("ONE_OF")))
                    .toList();
        }

        if (processingArguments.getLimitXxxOfCombinations() > 0) {
            int maxCombinations = Math.min(processingArguments.getLimitXxxOfCombinations(), payloadCombinationsBasedOnOneOfAndAnyOf.size());
            return payloadCombinationsBasedOnOneOfAndAnyOf.stream()
                    .limit(maxCombinations)
                    .toList();
        }
        return payloadCombinationsBasedOnOneOfAndAnyOf;
    }

    /**
     * When we deal with AnyOf or OneOf data types, we need to create multiple payloads based on the number of subtypes defined within the contract. This method will return all these combinations
     * based on the keywords 'ANY_OF' and 'ONE_OF' generated by the OpenAPIModelGenerator.
     *
     * @param initialPayload initial Payload including ONE_OF and ANY_OF information
     * @return a list of Payload associated with each ANY_OF, ONE_OF combination
     */
    private List<String> getPayloadCombinationsBasedOnOneOfAndAnyOf(String initialPayload) {
        List<String> result = new ArrayList<>();
        JsonElement jsonElement = JsonParser.parseString(initialPayload);

        if (jsonElement.isJsonArray()) {
            result = this.buildArray(this.addNewCombination(jsonElement.getAsJsonArray().get(0)));
        }
        if (jsonElement.isJsonObject()) {
            result = this.addNewCombination(jsonElement);
        }
        if (result.isEmpty()) {
            result.add(initialPayload);
        }

        return result;
    }

    private List<String> buildArray(List<String> singleElements) {
        return singleElements
                .stream()
                .map(element -> {
                    JsonElement jsonElement = JsonUtils.parseAsJsonElement(element);
                    return JsonUtils.GSON.toJson(List.of(jsonElement, jsonElement));
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * This gets all possible ONE_OF and ANY_OF combinations, including combinations between multiple ONE_OF/ANY_OF.
     *
     * @param jsonElement the initial JSON payload
     * @return a list with all possible ONE_OF and ANY_OF combinations based on the initial JSON payload
     */
    private List<String> addNewCombination(JsonElement jsonElement) {
        Set<String> result = new TreeSet<>();
        Deque<JsonElement> stack = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        stack.push(jsonElement);

        while (!stack.isEmpty()) {
            JsonElement current = stack.pop();
            String currentJson = current.toString();

            if (visited.contains(currentJson)) {
                continue; // Skip if already visited to avoid cycles
            }

            result.add(currentJson);
            visited.add(currentJson);

            Map<String, Map<String, JsonElement>> anyOfOrOneOfElements = getAnyOrOneOffElements("$", current);
            anyOfOrOneOfElements = joinCommonOneAndAnyOfs(anyOfOrOneOfElements);

            anyOfOrOneOfElements.forEach((pathKey, anyOfOrOneOf) -> {
                List<String> interimCombinationList = new ArrayList<>(result).stream()
                        .limit(Math.min(processingArguments.getLimitXxxOfCombinations(), result.size()))
                        .collect(Collectors.toCollection(ArrayList::new));

                result.clear();
                anyOfOrOneOf.forEach((key, value) ->
                        interimCombinationList.forEach(payload ->
                                result.add(JsonUtils.createValidOneOfAnyOfNode(payload, pathKey, key, value.toString(), anyOfOrOneOf.keySet()))));

                // Add elements to the stack for further processing
                result.stream()
                        .filter(json -> json.contains(ANY_OF) || json.contains(ONE_OF))
                        .map(JsonParser::parseString)
                        .forEach(stack::push);
            });
        }

        return new ArrayList<>(result);
    }

    private Map<String, Map<String, JsonElement>> joinCommonOneAndAnyOfs(Map<String, Map<String, JsonElement>> startingOneAnyOfs) {
        Set<String> keySet = startingOneAnyOfs.entrySet()
                .stream()
                .collect(Collectors.groupingBy(entry ->
                        entry.getKey().contains("_OF") ? entry.getKey().substring(0, entry.getKey().indexOf("_OF") - 3) : entry.getKey()))
                .keySet()
                .stream()
                .map(entry -> entry.replaceAll("\\.+$", ""))
                .collect(Collectors.toSet());

        List<Map<String, Map<String, JsonElement>>> listOfMap = keySet.stream()
                .map(key -> startingOneAnyOfs
                        .entrySet()
                        .stream()
                        .filter(entry -> entry.getKey().startsWith(key))
                        .collect(Collectors.toMap(stringMapEntry -> key, Map.Entry::getValue, (map1, map2) -> {
                            Map<String, JsonElement> newMap = new HashMap<>();
                            newMap.putAll(map1);
                            newMap.putAll(map2);
                            return newMap;
                        }))).toList();

        return listOfMap.stream().flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue));
    }


    private Map<String, Map<String, JsonElement>> getAnyOrOneOffElements(String jsonElementKey, JsonElement jsonElement) {
        Map<String, Map<String, JsonElement>> anyOrOneOfs = new HashMap<>();
        JsonObject jsonObject;
        if (jsonElement.isJsonObject()) {
            jsonObject = jsonElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> elementEntry : jsonObject.entrySet()) {
                if (elementEntry.getValue().isJsonArray() && !elementEntry.getValue().getAsJsonArray().isEmpty() && !elementEntry.getValue().getAsJsonArray().get(0).isJsonPrimitive() && !elementEntry.getValue().getAsJsonArray().get(0).isJsonNull()) {
                    anyOrOneOfs.putAll(this.getAnyOrOneOffElements(this.createArrayKey(jsonElementKey, elementEntry.getKey()), elementEntry.getValue().getAsJsonArray().get(0)));
                } else if (elementEntry.getKey().contains(ONE_OF) || elementEntry.getKey().contains(ANY_OF)) {
                    anyOrOneOfs.merge(this.createSimpleElementPath(jsonElementKey, elementEntry.getKey()),
                            Map.of(elementEntry.getKey(), elementEntry.getValue()), this::mergeMaps);
                } else if (isJsonValueOf(elementEntry.getValue(), elementEntry.getKey() + ONE_OF) || isJsonValueOf(elementEntry.getValue(), elementEntry.getKey() + ANY_OF)) {
                    anyOrOneOfs.merge(this.createSimpleElementPath(jsonElementKey, elementEntry.getKey()),
                            elementEntry.getValue().getAsJsonObject().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), this::mergeMaps);
                } else if (isAnyOrOneOfInChildren(elementEntry.getValue(), ANY_OF, ONE_OF)) {
                    anyOrOneOfs.putAll(this.getAnyOrOneOffElements(this.createSimpleElementPath(jsonElementKey, elementEntry.getKey()), elementEntry.getValue()));
                }
            }
        } else if (jsonElement.isJsonArray()) {
            JsonArray ja = jsonElement.getAsJsonArray();
            if (!ja.isEmpty() && !ja.get(0).isJsonNull() && !ja.get(0).isJsonPrimitive()) {
                anyOrOneOfs.putAll(getAnyOrOneOffElements(jsonElementKey, ja.get(0)));
            }
        }

        return anyOrOneOfs;
    }

    private boolean isJsonValueOf(JsonElement element, String startKey) {
        if (element.isJsonObject()) {
            Set<String> jsonKeySet = element.getAsJsonObject().keySet();
            return !jsonKeySet.isEmpty() && jsonKeySet.stream().allMatch(key -> key.startsWith(startKey));
        }
        return false;
    }

    private Map<String, JsonElement> mergeMaps(Map<String, JsonElement> firstMap, Map<String, JsonElement> secondMap) {
        Map<String, JsonElement> newMap = new HashMap<>();
        newMap.putAll(firstMap);
        newMap.putAll(secondMap);

        return newMap;
    }

    /**
     * Creates a fully qualified Json path.
     *
     * @param jsonElementKey the initial path
     * @param elementEntry   the next element path
     * @return a fully qualified JsonPath
     */
    private String createSimpleElementPath(String jsonElementKey, String elementEntry) {
        return jsonElementKey + "." + elementEntry;
    }

    private String createArrayKey(String jsonElementKey, String nextKey) {
        return jsonElementKey + "." + nextKey + "[*]";
    }

    private boolean isAnyOrOneOfInChildren(JsonElement element, String... toSearch) {
        String elementAsString = element.toString();

        return Arrays.stream(toSearch).anyMatch(elementAsString::contains);
    }

    private String squashAllOfElements(String payloadSample) {
        JsonElement jsonElement = JsonParser.parseString(payloadSample);
        JsonElement newElement = this.squashAllOf(jsonElement);

        return newElement.toString();
    }

    /**
     * When a sample payload is created by the OpenAPIModelGenerator, the ALL_OF elements are marked with the ALL_OF json key.
     * We now make sure that we combine all these elements under one root element.
     *
     * @param element the current Json element
     */
    private JsonElement squashAllOf(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject originalObject = element.getAsJsonObject();
            return buildNewObject(originalObject);
        } else if (element.isJsonArray()) {
            JsonArray originalArray = element.getAsJsonArray();
            JsonArray newArray = new JsonArray();
            for (JsonElement child : originalArray) {
                newArray.add(squashAllOf(child));
            }
            return newArray;
        } else {
            return element;
        }
    }

    @NotNull
    private JsonObject buildNewObject(JsonObject originalObject) {
        JsonObject newObject = new JsonObject();
        for (String key : originalObject.keySet()) {
            if (key.equalsIgnoreCase("ALL_OF") || key.endsWith("ALL_OF#null")) {
                JsonElement jsonElement = originalObject.get(key);
                if (jsonElement.isJsonObject()) {
                    JsonObject allOfObject = originalObject.getAsJsonObject(key);
                    mergeJsonObject(newObject, squashAllOf(allOfObject));
                } else {
                    newObject.add(key.substring(0, key.indexOf("ALL_OF")), squashAllOf(jsonElement));
                }
            } else if (!key.contains("ALL_OF")) {
                newObject.add(key, squashAllOf(originalObject.get(key)));
            }
        }
        return newObject;
    }

    private void mergeJsonObject(JsonObject original, JsonElement toMerge) {
        for (Map.Entry<String, JsonElement> entry : toMerge.getAsJsonObject().entrySet()) {
            original.add(entry.getKey(), entry.getValue());
        }
    }

    private List<String> getRequestContentTypes(Operation operation, OpenAPI openAPI) {
        List<String> requests = new ArrayList<>();
        if (operation.getRequestBody() != null) {
            Content defaultContent = buildDefaultContent();
            String reqBodyRef = operation.getRequestBody().get$ref();
            if (reqBodyRef != null) {
                operation.getRequestBody().setContent(openAPI.getComponents().getRequestBodies().get(this.getSchemaName(reqBodyRef)).getContent());
            }
            requests.addAll(new ArrayList<>(Optional.ofNullable(operation.getRequestBody().getContent()).orElse(defaultContent).keySet()));
        } else {
            requests.add(processingArguments.getDefaultContentType());
        }
        return requests;
    }

    private Content buildDefaultContent() {
        Content defaultContent = new Content();
        defaultContent.addMediaType(processingArguments.getDefaultContentType(), new MediaType());
        return defaultContent;
    }

    private Map<String, Set<String>> getResponseHeaders(Operation operation) {
        Map<String, Set<String>> responses = new HashMap<>();
        for (String responseCode : operation.getResponses().keySet()) {
            ApiResponse apiResponse = operation.getResponses().get(responseCode);

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
            Content defaultContent = buildDefaultContent();
            responses.put(responseCode, new ArrayList<>(Optional.ofNullable(apiResponse.getContent()).orElse(defaultContent).keySet()));
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
        OpenAPIModelGenerator generator = new OpenAPIModelGenerator(globalContext, validDataFormat, processingArguments.isUseExamples(), processingArguments.getSelfReferenceDepth());
        for (String responseCode : operation.getResponses().keySet()) {
            String responseSchemaRef = this.extractResponseSchemaRef(operation, responseCode);
            if (responseSchemaRef != null) {
                String respSchemaName = this.getSchemaName(responseSchemaRef);
                List<String> samples = this.generateSample(respSchemaName, generator, processingArguments.isGenerateAllXxxCombinationsForResponses());

                responses.put(responseCode, samples);
            } else {
                responses.put(responseCode, Collections.emptyList());
            }
        }
        return responses;
    }

    private String extractResponseSchemaRef(Operation operation, String responseCode) {
        for (String contentType : processingArguments.getContentType()) {
            ApiResponse apiResponse = operation.getResponses().get(responseCode);
            if (StringUtils.isNotEmpty(apiResponse.get$ref())) {
                return apiResponse.get$ref();
            }
            if (OpenApiUtils.hasContentType(apiResponse.getContent(), processingArguments.getContentType())) {
                Schema<?> respSchema = Optional.ofNullable(OpenApiUtils.getMediaTypeFromContent(apiResponse.getContent(), contentType).getSchema()).orElse(new Schema<>());

                if (CatsModelUtils.isArraySchema(respSchema)) {
                    return respSchema.getItems().get$ref();
                } else {
                    return respSchema.get$ref();
                }
            }
        }
        return null;
    }


    private Set<CatsHeader> extractHeaders(Operation operation) {
        Set<CatsHeader> headers = new HashSet<>();
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                if ("header".equalsIgnoreCase(param.getIn())) {
                    headers.add(CatsHeader.fromHeaderParameter(param));
                }
            }
        }

        return headers;
    }
}
