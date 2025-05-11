package com.endava.cats.util;

import com.endava.cats.model.ann.ExcludeTestCaseStrategy;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Splitter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.internal.ParseContextImpl;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONValue;
import net.minidev.json.parser.JSONParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_ENUMS_USING_TO_STRING;

/**
 * Utility class for JSON objects interaction.
 */
public abstract class JsonUtils {

    public static final int SELF_REF_DEPTH_MULTIPLIER = 6;

    /**
     * Used as a placeholder when a path is not found inside a given JSON
     */
    public static final String NOT_SET = "NOT_SET";

    /**
     * Used to prefix the first json element from an array when dealing with JSON arrays.
     */
    public static final String FIRST_ELEMENT_FROM_ROOT_ARRAY = "$[0]#";

    /**
     * Used to prefix array elements.
     */
    public static final String ALL_ELEMENTS_ROOT_ARRAY = "$[*]#";

    /**
     * A permissive JSON parser.
     */
    public static final JSONParser JSON_PERMISSIVE_PARSER = new JSONParser(JSONParser.MODE_PERMISSIVE);

    /**
     * A more strict JSON parser adhering to the RFC4627.
     */
    public static final Gson JSON_STRICT_PARSER = new GsonBuilder()
            .setStrictness(Strictness.STRICT)
            .disableHtmlEscaping()
            .create();

    private static final Pattern JSON_SQUARE_BR_KEYS = Pattern.compile("\\w+(\\[(?>[a-zA-Z0-9_*]*[a-zA-Z][a-zA-Z0-9_*]*)])+\\w*");
    private static final Pattern EMPTY_SQUARE_BRACKETS = Pattern.compile("\\w+\\[]\\w*");
    /**
     * Represents a wildcard pattern for JSON content type with optional parameters.
     */
    public static final String JSON_WILDCARD = "application\\/.*\\+?json;?.*";
    /**
     * Represents the JSON Patch content type.
     */
    public static final String JSON_PATCH = "application/merge-patch+json";

    /**
     * To not be used to serialize data ending in console or files. Use the TestCaseExporter serializer for that.
     */
    public static final Gson GSON = new GsonBuilder()
            .setStrictness(Strictness.LENIENT)
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .setExclusionStrategies(new ExcludeTestCaseStrategy())
            .registerTypeAdapter(Long.class, new LongTypeSerializer())
            .registerTypeAdapter(OffsetDateTime.class, new OffsetDatetimeTypeAdapter())
            .serializeNulls()
            .create();

    public static final Gson GSON_NO_PRETTY_PRINTING = new GsonBuilder()
            .setStrictness(Strictness.LENIENT)
            .disableHtmlEscaping()
            .setExclusionStrategies(new ExcludeTestCaseStrategy())
            .registerTypeAdapter(Long.class, new LongTypeSerializer())
            .registerTypeAdapter(OffsetDateTime.class, new OffsetDatetimeTypeAdapter())
            .serializeNulls()
            .create();

    public static final Configuration GSON_CONFIGURATION = Configuration.builder().jsonProvider(new GsonJsonProvider(GSON_NO_PRETTY_PRINTING)).mappingProvider(new GsonMappingProvider(GSON_NO_PRETTY_PRINTING)).build();

    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(JsonUtils.class);
    private static final Configuration JACKSON_JSON_NODE_CONFIGURATION = Configuration.builder()
            .mappingProvider(new JacksonMappingProvider())
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .build();

    private static final ObjectMapper CUSTOM_DEPTH_MAPPER = new ObjectMapper();
    private static final ObjectMapper SIMPLE_OBJECT_MAPPER = new ObjectMapper();


    private static final ParseContext PARSE_CONTEXT = new ParseContextImpl(JACKSON_JSON_NODE_CONFIGURATION);

    private JsonUtils() {
        //ntd
    }

    static {
        configureDepthAwareJacksonMapper(3);
        configureDefaultJacksonMapper();
    }

    private static void configureDefaultJacksonMapper() {
        configureObjectMapper(SIMPLE_OBJECT_MAPPER);
    }

    private static void configureDepthAwareJacksonMapper(int selfReferenceDepth) {
        int finalDepth = selfReferenceDepth * SELF_REF_DEPTH_MULTIPLIER;
        SimpleModule module = new SimpleModule();
        module.addSerializer(Object.class, new DepthLimitingSerializer(finalDepth));
        CUSTOM_DEPTH_MAPPER.registerModule(module);

        configureObjectMapper(CUSTOM_DEPTH_MAPPER);
    }

    private static void configureObjectMapper(ObjectMapper mapper) {
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(FAIL_ON_EMPTY_BEANS);
        mapper.enable(WRITE_ENUMS_USING_TO_STRING);
        mapper.enable(WRITE_BIGDECIMAL_AS_PLAIN);
        mapper.disable(WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(INDENT_OUTPUT);
    }

    public static ObjectMapper getCustomDepthMapper() {
        return CUSTOM_DEPTH_MAPPER;
    }

    public static ObjectMapper getSimpleObjectMapper() {
        return SIMPLE_OBJECT_MAPPER;
    }

    public static String serialize(Object obj) {
        try {
            return SIMPLE_OBJECT_MAPPER.writeValueAsString(obj);
        } catch (IOException e) {
            LOGGER.debug("Error serializing object: {}", e.getMessage());
            return null;
        }
    }

    public static String serializeWithDepthAwareSerializer(Object exampleObject) {
        try {
            StringWriter stringWriter = new StringWriter();
            JsonGenerator jsonGenerator = JsonUtils.getCustomDepthMapper().getFactory().createGenerator(stringWriter);
            JsonUtils.getCustomDepthMapper().writeValue(jsonGenerator, exampleObject);
            return stringWriter.toString();
        } catch (IOException e) {
            LOGGER.debug("Error writing large object as string: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks if the given value was not found when searched in a JSON using methods from this class.
     *
     * @param value the value to check
     * @return true if the value was not found, false otherwise
     */
    public static boolean isNotSet(String value) {
        return NOT_SET.equalsIgnoreCase(value);
    }


    /**
     * Checks if the given field is a valid map and has elements.
     *
     * @param payload the given payload
     * @param field   the field
     * @return true if the field is a map, false otherwise
     */
    public static boolean isValidMap(String payload, String field) {
        Object fieldValue = JsonUtils.getVariableFromJson(payload, field + ".keys()");

        return fieldValue != null && !isNotSet(String.valueOf(fieldValue)) && !"[]".equals(String.valueOf(fieldValue));
    }

    /**
     * Replaces "#" with "." inside the given path and escapes if needed.
     *
     * @param input the input path
     * @return a path replacing "#"  with "."
     */
    public static String sanitizeToJsonPath(String input) {
        return escapeFullPath(escapeSpaces(input.replace("#", ".")));
    }

    /**
     * Checks if the 2 input strings are equal as JSON elements.
     *
     * @param json1 the first json
     * @param json2 the second json
     * @return true if the 2 inputs are the same as JSON elements, false otherwise
     */
    public static boolean equalAsJson(String json1, String json2) {
        if (!isValidJson(json1) || !isValidJson(json2)) {
            return json1.equalsIgnoreCase(json2);
        }
        try {
            return JsonPath.parse(json1).jsonString().contentEquals(JsonPath.parse(json2).jsonString());
        } catch (UnsupportedOperationException e) {
            String json1Unescaped = StringEscapeUtils.unescapeJson(json1).replaceAll("(^[\"'])|([\"']$)", "");
            String json2Unescaped = StringEscapeUtils.unescapeJson(json2).replaceAll("(^[\"'])|([\"']$)", "");
            return JsonPath.parse(json1Unescaped).jsonString().contentEquals(JsonPath.parse(json2Unescaped).jsonString());
        }
    }

    /**
     * Parses the given payload as a JsonElement.
     *
     * @param payload the given payload
     * @return a JsonElement representing the given payload
     */
    public static JsonElement parseAsJsonElement(String payload) {
        JsonReader reader = new JsonReader(new StringReader(payload));
        reader.setStrictness(Strictness.LENIENT);
        return JsonParser.parseReader(reader);
    }

    /**
     * If payload is key value pair, it will convert it to a JSON object.
     *
     * @param payload the given payload
     * @return a JSON object
     */
    public static JsonElement parseOrConvertToJsonElement(String payload) {
        if (JsonUtils.isValidJson(payload) || !payload.contains("=")) {
            return JsonUtils.parseAsJsonElement(payload);
        }
        Map<String, String> keyValueMap = Splitter.on('&')
                .withKeyValueSeparator('=')
                .split(payload);

        return JsonUtils.parseAsJsonElement(GSON.toJson(keyValueMap));
    }

    /**
     * Checks if the given string is a valid JSON. Empty strings are not considered valid JSONs.
     *
     * @param text the given text
     * @return true if the input is a payload, false otherwise
     */
    public static boolean isValidJson(String text) {
        if (text == null) {
            return false;
        }
        try {
            var unused = JSON_STRICT_PARSER.fromJson(text, Object.class);
        } catch (Exception e) {
            return false;
        }
        return text.contains("{") || text.contains("]");
    }

    private static boolean testForPrimitiveOrThrow(String payload, String property) {
        return testForPredicateOrThrow(payload, property, JsonNode::isValueNode);
    }

    private static boolean testForPredicateOrThrow(String payload, String property, Predicate<JsonNode> testFunction) {
        if (isJsonArray(payload)) {
            property = FIRST_ELEMENT_FROM_ROOT_ARRAY + property;
        }

        JsonNode jsonNode = PARSE_CONTEXT.parse(payload).read(JsonUtils.sanitizeToJsonPath(property));
        return testFunction.test(jsonNode);
    }

    /**
     * Checks if the specified property in the JSON payload is a primitive type.
     *
     * @param payload  The JSON payload.
     * @param property The property to check for being a primitive type.
     * @return {@code true} if the specified property is a primitive type, {@code false} otherwise.
     */
    public static boolean isPrimitive(String payload, String property) {
        if (!isValidJson(payload)) {
            return false;
        }
        try {
            return testForPrimitiveOrThrow(payload, property);
        } catch (InvalidPathException e) {
            LOGGER.debug("Invalid path {}", property);
            return false;
        }
    }

    /**
     * Checks if the specified property in the JSON payload is an object type.
     *
     * @param payload  The JSON payload.
     * @param property The property to check for being an object type.
     * @return {@code true} if the specified property is an object type, {@code false} otherwise.
     */
    public static boolean isObject(String payload, String property) {
        if (!isValidJson(payload)) {
            return false;
        }
        try {
            return !testForPrimitiveOrThrow(payload, property);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * Checks if the specified property in the JSON payload is an array.
     *
     * @param payload  The JSON payload.
     * @param property The property to check for being an array.
     * @return {@code true} if the specified property is an array, {@code false} otherwise.
     */
    public static boolean isArray(String payload, String property) {
        if (!isValidJson(payload)) {
            return false;
        }
        try {
            return testForPredicateOrThrow(payload, property, JsonNode::isArray);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * Checks if the specified JSON payload represents a JSON array.
     *
     * @param payload The JSON payload to check.
     * @return {@code true} if the JSON payload is a JSON array, {@code false} otherwise.
     */
    public static boolean isJsonArray(String payload) {
        if (!isValidJson(payload)) {
            return false;
        }
        return JsonPath.parse(payload).read("$") instanceof JSONArray;
    }

    /**
     * Deletes the specified JSON node from the given JSON payload.
     *
     * @param payload The JSON payload from which to delete the node.
     * @param node    The JSON node to be deleted. Use JSONPath notation to specify the node.
     * @return The modified JSON payload after deleting the specified node.
     */
    public static String deleteNode(String payload, String node) {
        if (StringUtils.isNotBlank(payload)) {
            try {
                return JsonPath.parse(payload).delete(sanitizeToJsonPath(node)).jsonString();
            } catch (PathNotFoundException e) {
                return payload;
            }
        }
        return payload;
    }


    /**
     * Creates a node key based on the supplied strings in the form of {@code toEliminate.pathTowardsReplacement}.
     * If any of the given input strings contains a space, it will enclose the final key in [''].
     *
     * @param toEliminateKey start of the node key
     * @return a node key combining the given input
     */
    private static String escapeSpaces(String toEliminateKey) {
        if (toEliminateKey.chars().anyMatch(Character::isWhitespace)) {
            return "['" + toEliminateKey + "']";
        }
        return toEliminateKey;
    }

    public static String escapeFullPath(String jsonPath) {
        Matcher matcher = JSON_SQUARE_BR_KEYS.matcher(jsonPath);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(sb, jsonPathEscape(Matcher.quoteReplacement(matcher.group(0))));
        }
        matcher.appendTail(sb);

        String interim = sb.toString();

        Matcher emptyBracketMatcher = EMPTY_SQUARE_BRACKETS.matcher(interim);
        interim = emptyBracketMatcher.replaceAll(match -> jsonPathEscape(match.group(0)));

        return Arrays.stream(interim.split("\\.", -1))
                .map(item -> item.matches("^(([$@][a-zA-Z_-]+)|\\*|\\[\\w+\\])") ? jsonPathEscape(item) : item)
                .collect(Collectors.joining("."));
    }

    private static String jsonPathEscape(String str) {
        return "['" + str + "']";
    }

    /**
     * Retrieves the value of the specified JSON variable from the given JSON payload.
     *
     * @param jsonPayload The JSON payload from which to retrieve the variable.
     * @param value       The JSON variable to retrieve. Use JSONPath notation to specify the variable.
     * @return The value of the specified JSON variable. Returns {@code NOT_SET} if the variable is not found.
     */
    public static Object getVariableFromJson(String jsonPayload, String value) {
        try {
            DocumentContext jsonDoc = JsonPath.parse(jsonPayload);
            return jsonDoc.read(JsonUtils.sanitizeToJsonPath(value));
        } catch (JsonPathException | IllegalArgumentException e) {
            LOGGER.debug("Expected variable {} was not found. Setting to NOT_SET", value);
            return NOT_SET;
        }
    }

    /**
     * Checks if the given field is present in the given json payload.
     *
     * @param jsonPayload the input payload
     * @param field       the field to search
     * @return true if the field is found inside the given payload, false otherwise
     */
    public static boolean isFieldInJson(String jsonPayload, String field) {
        return !NOT_SET.equalsIgnoreCase(String.valueOf(getVariableFromJson(jsonPayload, field)));
    }

    /**
     * Checks if the given payload is null, empty or {}.
     *
     * @param payload the given payload
     * @return true if the payload is empty, false otherwise
     */
    public static boolean isEmptyPayload(String payload) {
        return payload == null || payload.isBlank() || payload.trim().equals("{}") || payload.trim().equals("\"{}\"");
    }

    /**
     * Determines if the current property is a cyclic reference to itself in the form of: prop#prop#prop#prop#... .
     *
     * @param currentProperty the property value
     * @param depth           depth of cyclic search
     * @return true if the given property is cyclic with the given depth, false otherwise
     */
    public static boolean isCyclicReference(String currentProperty, int depth) {
        if (currentProperty == null) {
            return false;
        }

        String[] tokens = Arrays.stream(currentProperty.split("[#_]", -1))
                .filter(StringUtils::isNotBlank)
                .toArray(String[]::new);
        if (tokens.length < depth) {
            return false;
        }

        Map<String, Long> tokenCounts = Arrays.stream(tokens)
                .map(String::toLowerCase)
                .map(item -> item.replace(".items", ""))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return tokenCounts.values().stream().anyMatch(count -> count > depth);
    }

    /**
     * Adds a new element inside the given JSON.
     *
     * @param initialPayload the initial JSON payload
     * @param newKey         the new element key
     * @param newValue       the new element value
     * @return a new payload starting with the initial JSON as base and with the new key and value
     */
    public static String replaceNewElement(String initialPayload, String pathToKey, String newKey, Object newValue) {
        LOGGER.debug("Adding new element {} with value {} to path {}", newKey, newValue, pathToKey);
        DocumentContext documentContext = JsonPath.parse(initialPayload);
        documentContext.put(sanitizeToJsonPath(pathToKey), newKey, newValue);

        return documentContext.jsonString();
    }

    /**
     * Converts the provided raw response to a JSON-formatted string.
     * If the raw response is already a valid JSON, it is returned as is. Otherwise,
     * a simplified JSON representation is created, including the first 500 characters
     * of the raw response.
     *
     * @param rawResponse The raw response string to be converted to JSON.
     * @return The JSON-formatted string representing the response.
     */
    public static String getAsJsonString(String rawResponse) {
        if (JsonUtils.isValidJson(rawResponse)) {
            return rawResponse;
        }
        return "{\"notAJson\": \"" + JSONValue.escape(rawResponse.substring(0, Math.min(500, rawResponse.length()))) + "\"}";
    }


    /**
     * Extracts all fields from a JSON string with their fully qualified names.
     *
     * @param jsonPayload the JSON string
     * @return a list of fully qualified field names
     */
    public static List<String> getAllFieldsOf(String jsonPayload) {
        List<String> fields = new ArrayList<>();
        JsonElement root = JsonParser.parseString(jsonPayload);
        traverseJson(root, "", fields);
        return fields;
    }

    /**
     * Inserts characters in the provided json key.
     *
     * @param json            the current json
     * @param currentFieldKey the current field key
     * @param valueToInsert   characters to insert
     * @return a new json with new characters inserted in the given key
     */
    public static String insertCharactersInFieldKey(String json, String currentFieldKey, String valueToInsert) {
        int indexOfHash = currentFieldKey.lastIndexOf("#");
        String lastPartOfField = currentFieldKey.substring(indexOfHash + 1);
        String firstPartOfField = currentFieldKey.substring(0, Math.max(indexOfHash, 0));

        Object currentFieldValue = getVariableFromJson(json, currentFieldKey);

        String newFieldKey = CatsUtil.insertInTheMiddle(lastPartOfField, valueToInsert, true);
        String inputJsonWithRemovedKey = deleteNode(json, currentFieldKey);

        String pathToKey = StringUtils.isBlank(firstPartOfField) ? "$" : firstPartOfField;

        return replaceNewElement(inputJsonWithRemovedKey, pathToKey, newFieldKey, currentFieldValue);
    }

    /**
     * Parses a JSON string into a Map using the custom depth mapper.
     *
     * @param json the JSON string to parse
     * @return a Map representation of the JSON
     */
    public static Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            return getCustomDepthMapper().readValue(json, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON input: " + json, e);
        }
    }

    /**
     * Converts a Map to a JSON string using the custom depth mapper.
     *
     * @param map the Map to convert
     * @return the JSON string representation of the Map
     */
    public static String toJsonString(Map<String, Object> map) {
        try {
            return getCustomDepthMapper().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert map to JSON string", e);
        }
    }

    private static void traverseJson(JsonElement element, String prefix, List<String> fields) {
        if (element.isJsonObject()) {
            JsonObject jsonObject = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String fieldName = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                fields.add(fieldName);
                traverseJson(entry.getValue(), fieldName, fields);
            }
        } else if (element.isJsonArray()) {
            JsonArray jsonArray = element.getAsJsonArray();
            for (int i = 0; i < jsonArray.size(); i++) {
                traverseJson(jsonArray.get(i), prefix + "[" + i + "]", fields);
            }
        }
    }
}
