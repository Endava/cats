package com.endava.cats.util;

import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.dsl.api.Parser;
import com.endava.cats.exception.CatsException;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.strategy.FuzzingStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import jakarta.enterprise.context.ApplicationScoped;
import net.minidev.json.JSONArray;
import net.minidev.json.parser.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logmanager.LogContext;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.endava.cats.util.CatsDSLWords.ADDITIONAL_PROPERTIES;
import static com.endava.cats.util.CatsDSLWords.ELEMENT;
import static com.endava.cats.util.CatsDSLWords.MAP_VALUES;

@ApplicationScoped
public class CatsUtil {
    public static final String FUZZER_KEY_DEFAULT = "*******";
    public static final String TEST_KEY_DEFAULT = "******";

    public static <T> List<T> filterAndPrintNotMatching(Collection<T> collection, Predicate<T> predicateToFilter, PrettyLogger logger, String messageWhenNotMatching, Function<T, String> functionToApplyToLoggedItems, String... params) {
        Map<Boolean, List<T>> results = collection.stream().collect(Collectors.partitioningBy(predicateToFilter));

        List<T> notMatching = results.get(false);

        notMatching.forEach(element -> logger.skip(messageWhenNotMatching, functionToApplyToLoggedItems.apply(element), params));

        return results.get(true);
    }

    public static void setCatsLogLevel(String level) {
        setLogLevel("com.endava.cats", level);
    }

    public static void setLogLevel(String pkg, String level) {
        LogContext.getLogContext().getLogger(pkg).setLevel(Level.parse(level.toUpperCase(Locale.ROOT)));
    }

    public void writeToYaml(String yaml, Map<String, Map<String, Object>> data) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.writeValue(new File(yaml), data);
    }


    public FuzzingResult replaceField(String payload, String jsonPropertyForReplacement, FuzzingStrategy fuzzingStrategyToApply) {
        return this.replaceField(payload, jsonPropertyForReplacement, fuzzingStrategyToApply, false);
    }

    /**
     * This method replaces the existing value of the {@code jsonPropertyForReplacement} with the supplied value.
     * For complex replacement like merging with refData values or processing the FuzzingStrategy use the {@code replaceField} method.
     *
     * @param payload                    the JSON payload
     * @param jsonPropertyForReplacement the JSON property path to replace
     * @param with                       the value to replace with
     * @return a result with the payload replaced
     */
    public FuzzingResult justReplaceField(String payload, String jsonPropertyForReplacement, Object with) {
        if (JsonUtils.isJsonArray(payload)) {
            jsonPropertyForReplacement = JsonUtils.ALL_ELEMENTS_ROOT_ARRAY + jsonPropertyForReplacement;
        }
        DocumentContext jsonDocument = JsonPath.parse(payload);
        replaceOldValueWithNewOne(jsonPropertyForReplacement, jsonDocument, with);

        return new FuzzingResult(jsonDocument.jsonString(), with);
    }

    public FuzzingResult replaceField(String payload, String jsonPropertyForReplacement, FuzzingStrategy fuzzingStrategyToApply, boolean mergeFuzzing) {
        if (StringUtils.isNotBlank(payload)) {
            String jsonPropToGetValue = jsonPropertyForReplacement;
            if (JsonUtils.isJsonArray(payload)) {
                jsonPropToGetValue = JsonUtils.FIRST_ELEMENT_FROM_ROOT_ARRAY + jsonPropertyForReplacement;
                jsonPropertyForReplacement = JsonUtils.ALL_ELEMENTS_ROOT_ARRAY + jsonPropertyForReplacement;
            }
            DocumentContext jsonDocument = JsonPath.parse(payload);
            Object oldValue = jsonDocument.read(JsonUtils.sanitizeToJsonPath(jsonPropToGetValue));
            if (oldValue instanceof JSONArray && !jsonPropToGetValue.contains("[*]")) {
                oldValue = jsonDocument.read("$." + jsonPropToGetValue + "[0]");
                jsonPropertyForReplacement = "$." + jsonPropertyForReplacement + "[*]";
            }
            Object valueToSet = fuzzingStrategyToApply.process(oldValue);
            if (mergeFuzzing) {
                valueToSet = FuzzingStrategy.mergeFuzzing(WordUtils.nullOrValueOf(oldValue), fuzzingStrategyToApply.getData());
            }
            replaceOldValueWithNewOne(jsonPropertyForReplacement, jsonDocument, valueToSet);

            return new FuzzingResult(jsonDocument.jsonString(), valueToSet);
        }
        return FuzzingResult.empty();
    }

    private static void replaceOldValueWithNewOne(String jsonPropertyForReplacement, DocumentContext jsonDocument, Object valueToSet) {
        if (JsonUtils.isValidJson(String.valueOf(valueToSet))) {
            if (areBothPropertyToReplaceAndValueToReplaceArrays(jsonPropertyForReplacement, valueToSet)) {
                jsonPropertyForReplacement = removeArrayTermination(jsonPropertyForReplacement);
            }
            try {
                jsonDocument.set(JsonUtils.sanitizeToJsonPath(jsonPropertyForReplacement), JsonUtils.GENERIC_PERMISSIVE_PARSER.parse(String.valueOf(valueToSet)));
            } catch (ParseException e) {
                throw new CatsException(e);
            }
        } else {
            jsonDocument.set(JsonUtils.sanitizeToJsonPath(jsonPropertyForReplacement), valueToSet);
        }
    }

    @NotNull
    private static String removeArrayTermination(String jsonPropertyForReplacement) {
        return jsonPropertyForReplacement.substring(0, jsonPropertyForReplacement.lastIndexOf("[*]"));
    }

    private static boolean areBothPropertyToReplaceAndValueToReplaceArrays(String jsonPropertyForReplacement, Object valueToSet) {
        return jsonPropertyForReplacement.endsWith("[*]") && valueToSet instanceof List;
    }

    /**
     * When parsing the custom fuzzer files the additionalProperties element will be parsed as:
     * {@code {topElement=metadata, mapValues={test1=value1,test2=value2}}}.
     *
     * @param currentPathValues current path values from custom fuzzer
     * @param payload           the existing payload
     * @return a payload with additionalProperties added
     */
    public String setAdditionalPropertiesToPayload(Map<String, Object> currentPathValues, String payload) {
        String additionalProperties = WordUtils.nullOrValueOf(currentPathValues.get(ADDITIONAL_PROPERTIES));
        if (additionalProperties != null && StringUtils.isNotBlank(payload)) {
            DocumentContext jsonDoc = JsonPath.parse(payload);
            String mapValues = additionalProperties;
            String prefix = "$";
            if (additionalProperties.contains(ELEMENT)) {
                String[] elements = additionalProperties.split(",", 2);
                String topElement = elements[0].replace(ELEMENT + "=", "").replace("{", "");
                mapValues = elements[1];
                jsonDoc.put(JsonPath.compile(prefix), topElement, new LinkedHashMap<>());
                prefix = prefix + "." + topElement;
            }
            setMapValues(jsonDoc, mapValues, prefix);

            return jsonDoc.jsonString();
        }
        return payload;
    }

    private void setMapValues(DocumentContext jsonDoc, String additionalProperties, String prefix) {
        String mapValues = additionalProperties.replace(MAP_VALUES + "=", "").replace("{", "").replace("}", "");
        for (String values : mapValues.split(",", -1)) {
            String[] entry = values.split("=", -1);
            jsonDoc.put(JsonPath.compile(prefix), entry[0].trim(), CatsDSLParser.parseAndGetResult(entry[1].trim(), Map.of(Parser.REQUEST, jsonDoc.jsonString())));
        }
    }
}