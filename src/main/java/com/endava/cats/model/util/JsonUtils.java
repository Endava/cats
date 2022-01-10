package com.endava.cats.model.util;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.ann.ExcludeTestCaseStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
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
import org.apache.commons.lang3.StringUtils;

import java.io.StringReader;

public abstract class JsonUtils {
    public static final String NOT_SET = "NOT_SET";
    public static final String FIRST_ELEMENT_FROM_ROOT_ARRAY = "$[0]#";
    public static final String ALL_ELEMENTS_ROOT_ARRAY = "$[*]#";
    public static final Gson GSON = new GsonBuilder()
            .setLenient()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .setExclusionStrategies(new ExcludeTestCaseStrategy())
            .registerTypeAdapter(Long.class, new LongTypeSerializer())
            .serializeNulls().create();
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

    public static boolean isHttpMethodWithPayload(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    public static JsonElement parseAsJsonElement(String payload) {
        JsonReader reader = new JsonReader(new StringReader(payload));
        reader.setLenient(true);
        return JsonParser.parseReader(reader);
    }

    public static boolean isValidJson(String text) {
        try {
            JsonParser.parseString(text);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static boolean isPrimitive(String payload, String property) {
        if (isJsonArray(payload)) {
            property = FIRST_ELEMENT_FROM_ROOT_ARRAY + property;
        }
        try {
            JsonNode jsonNode = PARSE_CONTEXT.parse(payload).read(JsonUtils.sanitizeToJsonPath(property));
            return jsonNode.isValueNode();
        } catch (PathNotFoundException e) {
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

    public static Object getVariableFromJson(String jsonPayload, String value) {
        DocumentContext jsonDoc = JsonPath.parse(jsonPayload);
        try {
            return jsonDoc.read(JsonUtils.sanitizeToJsonPath(value));
        } catch (JsonPathException e) {
            LOGGER.debug("Expected variable {} was not found. Setting to NOT_SET", value);
            return NOT_SET;
        }
    }
}
