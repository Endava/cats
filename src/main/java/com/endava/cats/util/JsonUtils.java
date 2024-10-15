package com.endava.cats.util;

import com.endava.cats.model.ann.ExcludeTestCaseStrategy;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.jayway.jsonpath.Option;
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
import net.minidev.json.parser.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.io.StringReader;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for JSON objects interaction.
 */
public abstract class JsonUtils {
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

    public static final Configuration SUPPRESS_EXCEPTIONS_CONFIGURATION = new Configuration.ConfigurationBuilder().options(Option.SUPPRESS_EXCEPTIONS).build();

    public static final Configuration GSON_CONFIGURATION = Configuration.builder().jsonProvider(new GsonJsonProvider(GSON_NO_PRETTY_PRINTING)).mappingProvider(new GsonMappingProvider(GSON_NO_PRETTY_PRINTING)).build();

    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(JsonUtils.class);
    private static final Configuration JACKSON_JSON_NODE_CONFIGURATION = Configuration.builder()
            .mappingProvider(new JacksonMappingProvider())
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .build();

    private static final ParseContext PARSE_CONTEXT = new ParseContextImpl(JACKSON_JSON_NODE_CONFIGURATION);

    private JsonUtils() {
        //ntd
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
            JSON_STRICT_PARSER.fromJson(text, Object.class);
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
     * This will either replace the {@code nodeKey} with the {@code nodeValue} or, if the given key is not found,
     * it will replace the {@code alternativeKey} with the {@code nodeValue} and eliminate all other keys
     * supplied in the {@code toEliminate} list.
     *
     * @param payload        the initial JSON payload
     * @param nodeKey        the key used to replace the {@code nodeValue}
     * @param alternativeKey alternative key to search for payloads inline-ing ONE_OF and ANY_OF elements, rather than grouping them under a single element
     * @param nodeValue      the value to be placed inside the {@code nodeKey}
     * @param toEliminate    additional keys to eliminate after replacing the {@code nodeKey} with the {{@code nodeValue}
     * @return a JSON payload with ONE_OF and ANY_OF elements eliminated and replaced with a single combination
     */
    public static String createValidOneOfAnyOfNode(String payload, String nodeKey, String alternativeKey, String nodeValue, Set<String> toEliminate) {
        try {
            if ("$".equals(nodeKey)) {
                return nodeValue;
            }
            if (!payload.contains("_OF")) {
                return payload;
            }
            String interimPayload = JsonPath.parse(payload).set(escapeFullPath(nodeKey), JSON_PERMISSIVE_PARSER.parse(nodeValue)).jsonString();
            DocumentContext finalPayload = removeElements(toEliminate, interimPayload, nodeKey.substring(0, nodeKey.lastIndexOf(".")));
            return finalPayload.jsonString();
        } catch (PathNotFoundException e) {
            String pathTowardsReplacement = nodeKey.substring(0, nodeKey.lastIndexOf("."));
            String replacementKey = getReplacementKey(nodeKey);
            if (payload.contains("_OF")) {
                String cleanPath = CatsModelUtils.eliminateDuplicatePart(nodeKey);
                String interimPayload = JsonPath.parse(payload, SUPPRESS_EXCEPTIONS_CONFIGURATION).renameKey(pathTowardsReplacement, alternativeKey, replacementKey).jsonString();

                if (!cleanPath.equalsIgnoreCase(nodeKey)) {
                    interimPayload = addElement(payload, pathTowardsReplacement, nodeValue);
                }

                interimPayload = checkIfArrayHasNestedKeysWithSameName(nodeKey, pathTowardsReplacement, replacementKey, interimPayload);

                DocumentContext finalPayload = removeElements(toEliminate, interimPayload, pathTowardsReplacement);

                return finalPayload.jsonString();
            }
            return payload;
        } catch (ParseException | InvalidPathException e) {
            LOGGER.debug("Could not add node {}", nodeKey);
            return payload;
        }
    }

    private static DocumentContext removeElements(Set<String> toEliminate, String interimPayload, String pathTowardsReplacement) {
        DocumentContext finalPayload = JsonPath.parse(interimPayload);
        toEliminate.forEach(toEliminateKey -> {
            try {
                if (toEliminateKey.contains(".")) {
                    toEliminateKey = "['" + toEliminateKey + "']";
                }
                String nodeToDelete = pathTowardsReplacement + "." + escapeSpaces(toEliminateKey);
                LOGGER.debug("to delete {}", nodeToDelete);
                finalPayload.delete(escapeFullPath(nodeToDelete));
            } catch (PathNotFoundException ex) {
                LOGGER.debug("Path not found when removing any_of/one_of: {}", ex.getMessage());
            }
        });
        return finalPayload;
    }

    public static String getReplacementKey(String nodeKey) {
        String initial = nodeKey.substring(nodeKey.lastIndexOf(".") + 1);
        if (initial.endsWith("[*]")) {
            return initial.substring(0, initial.length() - 3);
        }
        return initial;
    }

    /**
     * When having an array of composed objects CATS will generate something like:
     * <pre>{@code
     * {...
     *   "services":[
     *    {
     *      "services": {...}
     *    },
     *    {
     *      "services": {...}
     *    }
     * ]
     * ...}
     *
     * }</pre>
     * <p>
     * In this specific cases, this will make sure we eliminate the inner keys which match the array key
     */
    private static String checkIfArrayHasNestedKeysWithSameName(String nodeKey, String pathTowardsReplacement, String replacementKey, String interimPayload) {
        if (pathTowardsReplacement.endsWith(replacementKey + "[*]")) {
            String arrayKey = pathTowardsReplacement.substring(0, pathTowardsReplacement.lastIndexOf("["));
            List<Object> innerArrayObject = JsonPath.parse(interimPayload, SUPPRESS_EXCEPTIONS_CONFIGURATION).read(nodeKey);
            interimPayload = JsonPath.parse(interimPayload, SUPPRESS_EXCEPTIONS_CONFIGURATION).set(arrayKey, innerArrayObject).jsonString();
        }
        return interimPayload;
    }

    /**
     * Creates a node key based on the supplied strings in the form of {@code toEliminate.pathTowardsReplacement}.
     * If any of the given input strings contains a space, it will enclose the final key in [''].
     *
     * @param toEliminateKey start of the node key
     * @return a node key combining the given input
     */
    private static String escapeSpaces(String toEliminateKey) {
        if (toEliminateKey.contains(" ")) {
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
        String[] properties = Arrays.stream(currentProperty.split("[#_]", -1)).filter(StringUtils::isNotBlank).toArray(String[]::new);

        if (properties.length < depth) {
            return false;
        }

        for (int i = 0; i < properties.length - 1; i++) {
            for (int j = i + 1; j <= properties.length - 1; j++) {
                if (properties[i].equalsIgnoreCase(properties[j]) && j - i >= depth) {
                    LOGGER.trace("Found cyclic reference for {}", currentProperty);
                    return true;
                }
            }
        }

        return false;
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
        DocumentContext documentContext = JsonPath.parse(initialPayload);
        documentContext.put(pathToKey, sanitizeToJsonPath(newKey), newValue);

        return documentContext.jsonString();
    }

    public static String addElement(String initialPayload, String pathToKey, String newValue) {
        DocumentContext documentContext = JsonPath.parse(initialPayload, SUPPRESS_EXCEPTIONS_CONFIGURATION);
        DocumentContext newValueContext = JsonPath.parse(newValue, SUPPRESS_EXCEPTIONS_CONFIGURATION);
        Object keysToMerge = newValueContext.read("$");

        Object existingValue = documentContext.read(pathToKey);
        if (existingValue instanceof Map m && keysToMerge instanceof Map m2) {
            m.putAll(m2);
        }

        documentContext.set(pathToKey, existingValue);

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

    /**
     * This will do the Cyclic Schema Reference to avoid infinite loop
     *
     * @param currentProperty currentProperty name string along with all the parent object name separated by '#'
     * @param schemaRefMap    the propertyName and respective component schema reference path
     * @param depth           Fixed depth number for child objects
     * @return a boolean as true or false
     */
    public static boolean isCyclicSchemaReference(String currentProperty, Map<String, String> schemaRefMap, int depth) {
        String[] properties = Arrays.stream(currentProperty.split("#", -1)).filter(StringUtils::isNotBlank).toArray(String[]::new);

        for (int i = 0; i < properties.length - 1; i++) {
            for (int j = i + 1; j <= properties.length - 1; j++) {
                String iKeyToSearch = Arrays.stream(properties).limit(i).collect(Collectors.joining("#"));
                String jKeyToSearch = Arrays.stream(properties).limit(j).collect(Collectors.joining("#"));
                String iRef = schemaRefMap.get(iKeyToSearch);
                String jRef = schemaRefMap.get(jKeyToSearch);
                if (((iRef != null && iRef.equalsIgnoreCase(jRef)) || properties[j].equalsIgnoreCase(properties[j - 1])) && j - i >= depth) {
                    LOGGER.trace("Found cyclic dependencies for {}", currentProperty);
                    return true;
                }
            }
        }

        return false;
    }
}
