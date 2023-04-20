package com.endava.cats.util;

import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.dsl.api.Parser;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.strategy.FuzzingStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logmanager.LogContext;

import javax.enterprise.context.ApplicationScoped;
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
    public static final String TEST_KEY_DEFAULT = "**********";

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


    public FuzzingResult replaceField(String payload, String jsonPropertyForReplacement, FuzzingStrategy fuzzingStrategyToApply, boolean mergeFuzzing) {
        if (StringUtils.isNotBlank(payload)) {
            String jsonPropToGetValue = jsonPropertyForReplacement;
            if (JsonUtils.isJsonArray(payload)) {
                jsonPropToGetValue = JsonUtils.FIRST_ELEMENT_FROM_ROOT_ARRAY + jsonPropertyForReplacement;
                jsonPropertyForReplacement = JsonUtils.ALL_ELEMENTS_ROOT_ARRAY + jsonPropertyForReplacement;
            }
            DocumentContext context = JsonPath.parse(payload);
            Object oldValue = context.read(JsonUtils.sanitizeToJsonPath(jsonPropToGetValue));
            if (oldValue instanceof JSONArray && !jsonPropToGetValue.contains("[*]")) {
                oldValue = context.read("$." + jsonPropToGetValue + "[0]");
                jsonPropertyForReplacement = "$." + jsonPropertyForReplacement + "[*]";
            }
            Object valueToSet = fuzzingStrategyToApply.process(oldValue);
            if (mergeFuzzing) {
                valueToSet = FuzzingStrategy.mergeFuzzing(this.nullOrValueOf(oldValue), fuzzingStrategyToApply.getData());
            }
            context.set(JsonUtils.sanitizeToJsonPath(jsonPropertyForReplacement), valueToSet);

            return new FuzzingResult(context.jsonString(), valueToSet);
        }
        return FuzzingResult.empty();
    }

    private String nullOrValueOf(Object object) {
        return object == null ? null : String.valueOf(object);
    }


    /**
     * When parsing the custom fuzzer files the additionalProperties element will be parsed as:
     * {@code {topElement=metadata, mapValues={test1=value1,test2=value2}}}.
     *
     * @param currentPathValues current path values from custom fuzzer
     * @param payload           the existing payload
     * @return a payload with additionalProperties added
     */
    public String setAdditionalPropertiesToPayload(Map<String, String> currentPathValues, String payload) {
        String additionalProperties = currentPathValues.get(ADDITIONAL_PROPERTIES);
        if (!"null".equalsIgnoreCase(additionalProperties) && additionalProperties != null && StringUtils.isNotBlank(payload)) {
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
        for (String values : mapValues.split(",")) {
            String[] entry = values.split("=");
            jsonDoc.put(JsonPath.compile(prefix), entry[0].trim(), CatsDSLParser.parseAndGetResult(entry[1].trim(), Map.of(Parser.REQUEST, jsonDoc.jsonString())));
        }
    }
}