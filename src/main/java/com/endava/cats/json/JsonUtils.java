package com.endava.cats.json;

import com.endava.cats.model.ann.ExcludeTestCaseStrategy;
import com.endava.cats.model.util.LongTypeSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.internal.ParseContextImpl;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import net.minidev.json.JSONArray;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.commons.lang3.StringUtils;

import java.io.StringReader;
import java.util.Set;
import java.util.function.Predicate;

public abstract class JsonUtils {
    public static final String NOT_SET = "NOT_SET";
    public static final String FIRST_ELEMENT_FROM_ROOT_ARRAY = "$[0]#";
    public static final String ALL_ELEMENTS_ROOT_ARRAY = "$[*]#";
    public static final JSONParser GENERIC_PERMISSIVE_PARSER = new JSONParser(JSONParser.MODE_PERMISSIVE);
    public static final JSONParser JSON_STRICT_PARSER = new JSONParser(JSONParser.MODE_STRICTEST);

    public static final Gson GSON = new GsonBuilder()
            .setLenient()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .setExclusionStrategies(new ExcludeTestCaseStrategy())
            .registerTypeAdapter(Long.class, new LongTypeSerializer())
            .serializeNulls()
            .create();

    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(JsonUtils.class);
    private static final Configuration JACKSON_JSON_NODE_CONFIGURATION = Configuration.builder()
            .mappingProvider(new JacksonMappingProvider())
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .build();
    private static final ParseContext PARSE_CONTEXT = new ParseContextImpl(JACKSON_JSON_NODE_CONFIGURATION);

    private JsonUtils() {
        //ntd
    }

    public static String sanitizeToJsonPath(String input) {
        return input.replace("#", ".");
    }

    public static boolean equalAsJson(String json1, String json2) {
        return JsonPath.parse(json1).jsonString().contentEquals(JsonPath.parse(json2).jsonString());
    }

    public static JsonElement parseAsJsonElement(String payload) {
        JsonReader reader = new JsonReader(new StringReader(payload));
        reader.setLenient(true);
        return JsonParser.parseReader(reader);
    }

    public static boolean isValidJson(String text) {
        try {
            JSON_STRICT_PARSER.parse(text);
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

    public static boolean isPrimitive(String payload, String property) {
        try {
            return testForPrimitiveOrThrow(payload, property);
        } catch (PathNotFoundException e) {
            return false;
        }
    }

    public static boolean isObject(String payload, String property) {
        try {
            return !testForPrimitiveOrThrow(payload, property);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    public static boolean isArray(String payload, String property) {
        try {
            return testForPredicateOrThrow(payload, property, JsonNode::isArray);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    public static boolean isJsonArray(String payload) {
        return JsonPath.parse(payload).read("$") instanceof JSONArray;
    }

    public static String deleteNode(String payload, String node) {
        if (StringUtils.isNotBlank(payload)) {
            try {
                return JsonPath.parse(payload).delete(JsonUtils.sanitizeToJsonPath(node)).jsonString();
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
            return JsonPath.parse(payload).set(nodeKey, GENERIC_PERMISSIVE_PARSER.parse(nodeValue)).jsonString();
        } catch (ParseException e) {
            LOGGER.debug("Could not add node {}", nodeKey);
            return payload;
        } catch (PathNotFoundException e) {
            String pathTowardsReplacement = nodeKey.substring(0, nodeKey.lastIndexOf("."));
            String replacementKey = nodeKey.substring(nodeKey.lastIndexOf(".") + 1);
            if (payload.contains("_OF")) {
                String interimPayload = JsonPath.parse(payload).renameKey(pathTowardsReplacement, alternativeKey, replacementKey).jsonString();

                DocumentContext finalPayload = JsonPath.parse(interimPayload);
                toEliminate.forEach(toEliminateKey -> {
                    try {
                        finalPayload.delete(pathTowardsReplacement + "." + toEliminateKey);
                    } catch (PathNotFoundException ex) {
                        LOGGER.debug("Path not found when removing any_of/one_of: {}", ex.getMessage());
                    }
                });
                return finalPayload.jsonString();
            }
            return payload;
        }
    }

    public static Object getVariableFromJson(String jsonPayload, String value) {
        try {
            DocumentContext jsonDoc = JsonPath.parse(jsonPayload);
            return jsonDoc.read(JsonUtils.sanitizeToJsonPath(value));
        } catch (JsonPathException | IllegalArgumentException e) {
            LOGGER.debug("Expected variable {} was not found. Setting to NOT_SET", value);
            return NOT_SET;
        }
    }

    public static boolean isFieldInJson(String jsonPayload, String field) {
        return !NOT_SET.equalsIgnoreCase(String.valueOf(getVariableFromJson(jsonPayload, field)));
    }

    public static boolean isEmptyPayload(String payload) {
        return payload == null || payload.isBlank() || payload.trim().equals("{}") || payload.trim().equals("\"{}\"");
    }

    public static boolean isCyclicReference(String currentProperty, int depth) {
        String[] properties = currentProperty.split("#");

        if (properties.length < depth) {
            return false;
        }

        for (int i = 0; i < properties.length - 1; i++) {
            for (int j = i + 1; j <= properties.length - 1; j++) {
                if (properties[i].equalsIgnoreCase(properties[j])) {
                    LOGGER.trace("Found cyclic dependencies for {}", currentProperty);
                    return true;
                }
            }
        }

        return false;
    }
}
