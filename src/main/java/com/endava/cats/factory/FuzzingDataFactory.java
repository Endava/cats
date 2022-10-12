package com.endava.cats.factory;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.generator.PayloadGenerator;
import com.endava.cats.model.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * This class is responsible for creating {@link com.endava.cats.model.FuzzingData} objects based on the supplied OpenApi paths
 */
@ApplicationScoped
public class FuzzingDataFactory {
    private static final String SYNTH_SCHEMA_NAME = "CatsGetSchema";
    private static final String ANY_OF = "ANY_OF";
    private static final String ONE_OF = "ONE_OF";

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(FuzzingDataFactory.class);
    private final FilesArguments filesArguments;
    private final ProcessingArguments processingArguments;
    private final CatsGlobalContext globalContext;

    @Inject
    public FuzzingDataFactory(FilesArguments filesArguments, ProcessingArguments processingArguments, CatsGlobalContext catsGlobalContext) {
        this.filesArguments = filesArguments;
        this.processingArguments = processingArguments;
        this.globalContext = catsGlobalContext;
    }

    /**
     * Creates a list of FuzzingData objects that will be used to fuzz the provided PathItems. The reason there is more than one FuzzingData object is due
     * to cases when the contract uses OneOf or AnyOf composite objects which causes the payload to have more than one variation.
     *
     * @param path the path from the contract
     * @param item the PathItem containing the details about the interaction with the path
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
    private Set<String> extractQueryParams(ObjectSchema schema) {
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
    private ObjectSchema createSyntheticSchemaForGet(List<Parameter> operationParameters) {
        ObjectSchema syntheticSchema = new ObjectSchema();
        syntheticSchema.setProperties(new LinkedHashMap<>());
        List<String> required = new ArrayList<>();
        for (Parameter parameter : Optional.ofNullable(operationParameters).orElseGet(Collections::emptyList)) {
            if (("path".equalsIgnoreCase(parameter.getIn()) || "query".equalsIgnoreCase(parameter.getIn()))
                    && filesArguments.getUrlParamsList().stream().noneMatch(urlParam -> urlParam.startsWith(parameter.getName()))) {
                parameter.getSchema().setName(parameter.getName() + "|" + parameter.getIn());
                syntheticSchema.addProperties(parameter.getName(), parameter.getSchema());
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
        List<FuzzingData> fuzzingDataList = new ArrayList<>();
        MediaType mediaType = this.getMediaType(operation, openAPI);

        if (mediaType == null) {
            logger.info("Content type not supported for path {}, method {}. CATS detects application/json by default. " + "You might try to supply the custom content type using --contentType argument", path, method);
            return Collections.emptyList();
        }
        List<String> reqSchemaNames = this.getCurrentRequestSchemaName(mediaType);
        logger.debug("Request schema names identified for path {}, method {}: {}", path, method, reqSchemaNames);

        Map<String, List<String>> responses = this.getResponsePayloads(operation, operation.getResponses().keySet());
        Map<String, List<String>> responsesContentTypes = this.getResponseContentTypes(operation, operation.getResponses().keySet());
        List<String> requestContentTypes = this.getRequestContentTypes(operation, openAPI);
        logger.debug("Request content types for path {}, method {}: {}", path, method, requestContentTypes);

        for (String reqSchemaName : reqSchemaNames) {
            List<String> payloadSamples = this.getRequestPayloadsSamples(mediaType, reqSchemaName);
            fuzzingDataList.addAll(payloadSamples.stream()
                    .map(payload -> FuzzingData.builder()
                            .method(method).path(path)
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
                            .selfReferenceDepth(processingArguments.getSelfReferenceDepth())
                            .build()).toList());
        }

        return List.copyOf(fuzzingDataList);
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
        ObjectSchema syntheticSchema = this.createSyntheticSchemaForGet(operation.getParameters());

        globalContext.getSchemaMap().put(SYNTH_SCHEMA_NAME + operation.getOperationId(), syntheticSchema);
        Set<String> queryParams = this.extractQueryParams(syntheticSchema);
        logger.debug("Query params for path {}, method {}: {}", path, method, queryParams);

        List<String> payloadSamples = this.getRequestPayloadsSamples(null, SYNTH_SCHEMA_NAME + operation.getOperationId());
        Map<String, List<String>> responsesContentTypes = this.getResponseContentTypes(operation, operation.getResponses().keySet());
        Map<String, List<String>> responses = this.getResponsePayloads(operation, operation.getResponses().keySet());
        List<String> requestContentTypes = this.getRequestContentTypes(operation, openAPI);
        logger.debug("Request content types for path {}, method {}: {}", path, method, requestContentTypes);

        return payloadSamples.stream()
                .map(payload -> FuzzingData.builder()
                        .method(method).path(path)
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
                        .selfReferenceDepth(processingArguments.getSelfReferenceDepth()).build())
                .toList();
    }


    /**
     * We get the definition name for each request type. This also includes cases when we have AnyOf or OneOf schemas.
     *
     * @param mediaType the media type extracted from the Operation
     * @return a list of request scheme names from the current media type
     */
    private List<String> getCurrentRequestSchemaName(MediaType mediaType) {
        List<String> reqSchemas = new ArrayList<>();
        String currentRequestSchema = mediaType.getSchema().get$ref();

        if (currentRequestSchema == null && mediaType.getSchema() instanceof ArraySchema arraySchema) {
            currentRequestSchema = arraySchema.getItems().get$ref();
        }
        if (currentRequestSchema != null) {
            reqSchemas.add(this.getSchemaName(currentRequestSchema));
        } else if (mediaType.getSchema() instanceof ComposedSchema composedSchema) {
            if (composedSchema.getAnyOf() != null) {
                composedSchema.getAnyOf().forEach(innerSchema -> reqSchemas.add(this.getSchemaName(innerSchema.get$ref())));
            }
            if (composedSchema.getOneOf() != null) {
                composedSchema.getOneOf().forEach(innerSchema -> reqSchemas.add(this.getSchemaName(innerSchema.get$ref())));
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
        PayloadGenerator generator = new PayloadGenerator(globalContext, processingArguments.isUseExamples(), processingArguments.getSelfReferenceDepth());
        List<String> result = this.generateSample(reqSchemaName, generator);

        if (mediaType != null && mediaType.getSchema() instanceof ArraySchema) {
            /*when dealing with ArraySchemas we make sure we have 2 elements in the array*/
            result = result.stream().map(payload -> "[" + payload + "," + payload + "]").toList();
        }
        return result;
    }

    private List<String> generateSample(String reqSchemaName, PayloadGenerator generator) {
        Map<String, String> examples = generator.generate(reqSchemaName);
        if (examples.isEmpty()) {
            throw new IllegalArgumentException("Scheme is not declared: " + reqSchemaName);
        }
        String payloadSample = examples.get("example");

        payloadSample = this.squashAllOfElements(payloadSample);
        return this.getPayloadCombinationsBasedOnOneOfAndAnyOf(payloadSample);
    }

    /**
     * When we deal with AnyOf or OneOf data types, we need to create multiple payloads based on the number of sub-types defined within the contract. This method will return all these combinations
     * based on the keywords 'ANY_OF' and 'ONE_OF' generated by the PayloadGenerator.
     *
     * @param initialPayload initial Payload including ONE_OF and ANY_OF information
     * @return a list of Payload associated with each ANY_OF, ONE_OF combination
     */
    private List<String> getPayloadCombinationsBasedOnOneOfAndAnyOf(String initialPayload) {
        List<String> result = new ArrayList<>();
        JsonElement jsonElement = JsonParser.parseString(initialPayload);

        if (jsonElement.isJsonArray()) {
            result = this.addNewCombination(jsonElement.getAsJsonArray().get(0));
        }
        if (jsonElement.isJsonObject()) {
            result = this.addNewCombination(jsonElement);
        }
        if (result.isEmpty()) {
            result.add(initialPayload);
        }

        return result;
    }

    /**
     * This gets all possible ONE_OF and ANY_OF combinations, including combinations between multiple ONE_OF/ANY_OF.
     *
     * @param jsonElement the initial JSON payload
     * @return a list with all possible ONE_OF and ANY_OF combinations based on the initial JSON payload
     */
    private List<String> addNewCombination(JsonElement jsonElement) {
        List<String> result = new ArrayList<>();
        result.add(jsonElement.toString());
        Map<String, Map<String, JsonElement>> anyOfOrOneOfElements = this.getAnyOrOneOffElements("$", jsonElement);
        anyOfOrOneOfElements = this.joinCommonOneAndAnyOfs(anyOfOrOneOfElements);

        anyOfOrOneOfElements.forEach((pathKey, anyOfOrOneOf) -> {
            List<String> interimCombinationList = new ArrayList<>(result);
            result.clear();
            anyOfOrOneOf.forEach((key, value) ->
                    interimCombinationList.forEach(payload ->
                            result.add(JsonUtils.createValidOneOfAnyOfNode(payload, pathKey, key, value.toString(), anyOfOrOneOf.keySet()))));
        });

        return result;
    }

    private Map<String, Map<String, JsonElement>> joinCommonOneAndAnyOfs(Map<String, Map<String, JsonElement>> startingOneAnyOfs) {
        Set<String> keySet = startingOneAnyOfs.entrySet()
                .stream()
                .collect(Collectors.groupingBy(entry ->
                        entry.getKey().contains("_OF") ? entry.getKey().substring(0, entry.getKey().indexOf("_OF") - 3) : entry.getKey()))
                .keySet();

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

        for (Map.Entry<String, JsonElement> elementEntry : jsonElement.getAsJsonObject().entrySet()) {
            if (elementEntry.getValue().isJsonArray() && !elementEntry.getValue().getAsJsonArray().isEmpty() && !elementEntry.getValue().getAsJsonArray().get(0).isJsonPrimitive() && !elementEntry.getValue().getAsJsonArray().get(0).isJsonNull()) {
                anyOrOneOfs.putAll(this.getAnyOrOneOffElements(this.createArrayKey(jsonElementKey, elementEntry.getKey()), elementEntry.getValue().getAsJsonArray().get(0)));
            } else if (elementEntry.getKey().contains(ONE_OF) || elementEntry.getKey().contains(ANY_OF)) {
                anyOrOneOfs.merge(this.createSimpleElementPath(jsonElementKey, elementEntry.getKey()),
                        Map.of(elementEntry.getKey(), elementEntry.getValue()), this.mergeMapsBiFunction());
            } else if (isJsonValueOf(elementEntry.getValue(), ONE_OF) || isJsonValueOf(elementEntry.getValue(), ANY_OF)) {
                anyOrOneOfs.merge(this.createSimpleElementPath(jsonElementKey, elementEntry.getKey()),
                        elementEntry.getValue().getAsJsonObject().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), this.mergeMapsBiFunction());
            } else if (isAnyOrOneOfInChildren(elementEntry.getValue(), ANY_OF, ONE_OF)) {
                anyOrOneOfs.putAll(this.getAnyOrOneOffElements(this.createSimpleElementPath(jsonElementKey, elementEntry.getKey()), elementEntry.getValue()));
            }
        }

        return anyOrOneOfs;
    }

    @NotNull
    private BiFunction<Map<String, JsonElement>, Map<String, JsonElement>, Map<String, JsonElement>> mergeMapsBiFunction() {
        return (firstMap, secondMap) -> {
            Map<String, JsonElement> newMap = new HashMap<>();
            newMap.putAll(firstMap);
            newMap.putAll(secondMap);

            return newMap;
        };
    }

    /**
     * Creates a fully qualified Json path.
     * If the initial path ends with an array, it won't add the elementEntry.
     *
     * @param jsonElementKey the initial path
     * @param elementEntry   the next element path
     * @return a fully qualified JsonPath
     */
    private String createSimpleElementPath(String jsonElementKey, String elementEntry) {
        if (jsonElementKey.contains("[*]")) {
            return jsonElementKey;
        }
        return jsonElementKey + "." + elementEntry;
    }

    @NotNull
    private String createArrayKey(String jsonElementKey, String nextKey) {
        return jsonElementKey + "." + nextKey + "[*]";
    }

    private boolean isAnyOrOneOfInChildren(JsonElement element, String... toSearch) {
        String elementAsString = element.toString();

        return Arrays.stream(toSearch).anyMatch(elementAsString::contains);
    }

    private boolean isJsonValueOf(JsonElement element, String startKey) {
        if (element.isJsonObject()) {
            return element.getAsJsonObject().keySet().stream().anyMatch(key -> key.contains(startKey));
        }
        return false;
    }

    private String squashAllOfElements(String payloadSample) {
        JsonElement jsonElement = JsonParser.parseString(payloadSample);
        this.squashAllOf(jsonElement);

        return jsonElement.toString();
    }

    /**
     * When a sample payload is created by the PayloadGenerator, the ALL_OF elements are marked with the ALL_OF json key.
     * We now make sure that we combine all these elements under one root element.
     *
     * @param element the current Json element
     */
    private void squashAllOf(JsonElement element) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                squashAllOf(entry.getValue());
                if (entry.getKey().equalsIgnoreCase("ALL_OF")) {
                    element.getAsJsonObject().remove(entry.getKey());
                    for (Map.Entry<String, JsonElement> allOfEntry : entry.getValue().getAsJsonObject().entrySet()) {
                        element.getAsJsonObject().add(allOfEntry.getKey(), allOfEntry.getValue());
                    }
                    break;
                }
            }
        } else if (element.isJsonArray()) {
            for (int i = 0; i < element.getAsJsonArray().size(); i++) {
                squashAllOf(element.getAsJsonArray().get(i));
            }
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

    private Map<String, List<String>> getResponseContentTypes(Operation operation, Set<String> responseCodes) {
        Map<String, List<String>> responses = new HashMap<>();
        for (String responseCode : responseCodes) {
            ApiResponse apiResponse = operation.getResponses().get(responseCode);
            Content defaultContent = buildDefaultContent();
            responses.put(responseCode, new ArrayList<>(Optional.ofNullable(apiResponse.getContent()).orElse(defaultContent).keySet()));
        }

        return responses;
    }

    /**
     * We need to get JSON structural samples for each response code documented into the contract. This includes ONE_OF or ANY_OF combinations.
     *
     * @param operation     the current OpenAPI operation
     * @param responseCodes the list of response codes associated to the current Operation
     * @return a list if response payloads associated to each response code
     */
    private Map<String, List<String>> getResponsePayloads(Operation operation, Set<String> responseCodes) {
        Map<String, List<String>> responses = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        PayloadGenerator generator = new PayloadGenerator(globalContext, processingArguments.isUseExamples(), processingArguments.getSelfReferenceDepth());
        for (String responseCode : responseCodes) {
            String responseSchemaRef = this.extractResponseSchemaRef(operation, responseCode);
            if (responseSchemaRef != null) {
                String respSchemaName = this.getSchemaName(responseSchemaRef);
                List<String> samples = this.generateSample(respSchemaName, generator);

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
                Schema<?> respSchema = OpenApiUtils.getMediaTypeFromContent(apiResponse.getContent(), contentType).getSchema();
                if (respSchema instanceof ArraySchema arraySchema) {
                    return arraySchema.getItems().get$ref();
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
                if (param.getIn().equalsIgnoreCase("header")) {
                    headers.add(CatsHeader.fromHeaderParameter(param));
                }
            }
        }

        return headers;
    }
}
