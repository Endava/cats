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
import org.apache.commons.lang3.math.NumberUtils;
import org.jboss.logmanager.LogContext;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.endava.cats.util.CatsDSLWords.ADDITIONAL_PROPERTIES;
import static com.endava.cats.util.CatsDSLWords.ELEMENT;
import static com.endava.cats.util.CatsDSLWords.MAP_VALUES;

/**
 * Some utility methods that don't fit in other classes.
 */
@ApplicationScoped
public class CatsUtil {
    private static final String COMMA = ", ";
    private static final String N_A = "N/A";

    /**
     * Filters a collection based on a specified predicate and prints items that do not match the predicate using a logger.
     * Returns a list of items that match the predicate.
     *
     * @param <T>                          the type of elements in the collection
     * @param collection                   the collection to filter
     * @param predicateToFilter            the predicate to apply for filtering
     * @param logger                       the logger to use for printing not matching items
     * @param messageWhenNotMatching       the message to log when an item does not match the predicate
     * @param functionToApplyToLoggedItems a function to apply to logged items for additional information
     * @param params                       additional parameters to include in the log message
     * @return a list of items that match the predicate
     */
    public static <T> List<T> filterAndPrintNotMatching(Collection<T> collection, Predicate<T> predicateToFilter, PrettyLogger logger, String messageWhenNotMatching, Function<T, String> functionToApplyToLoggedItems, String... params) {
        Map<Boolean, List<T>> results = collection.stream().collect(Collectors.partitioningBy(predicateToFilter));

        List<T> notMatching = results.get(false);

        notMatching.forEach(element -> logger.skip(messageWhenNotMatching, functionToApplyToLoggedItems.apply(element), params));

        return results.get(true);
    }

    /**
     * Sets the log level for the Cats framework.
     *
     * @param level the desired log level (e.g., "DEBUG", "INFO", "WARN", "ERROR")
     */
    public static void setCatsLogLevel(String level) {
        setLogLevel("com.endava.cats", level);
    }

    /**
     * Sets the log level for a specified package.
     *
     * @param pkg   the package for which to set the log level
     * @param level the desired log level (e.g., "DEBUG", "INFO", "WARN", "ERROR")
     */
    public static void setLogLevel(String pkg, String level) {
        LogContext.getLogContext().getLogger(pkg).setLevel(Level.parse(level.toUpperCase(Locale.ROOT)));
    }

    /**
     * Writes a Map of data to a YAML file using the Jackson ObjectMapper.
     *
     * @param yaml the path to the YAML file to be written
     * @param data the data to be written to the YAML file
     * @throws IOException if an I/O error occurs during the writing process
     */
    public void writeToYaml(String yaml, Map<String, Map<String, Object>> data) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.writeValue(new File(yaml), data);
    }


    /**
     * Replaces a specific field in the given payload using the provided fuzzing strategy.
     *
     * @param payload                    the original payload containing the field to be replaced
     * @param jsonPropertyForReplacement the JSON property representing the field to be replaced
     * @param fuzzingStrategyToApply     the fuzzing strategy to apply for replacement
     * @return a FuzzingResult containing the modified payload and information about the replacement
     */
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

    /**
     * Replaces a specific field in the given payload using the provided fuzzing strategy.
     *
     * @param payload                    the original payload containing the field to be replaced
     * @param jsonPropertyForReplacement the JSON property representing the field to be replaced
     * @param fuzzingStrategyToApply     the fuzzing strategy to apply for replacement
     * @param mergeFuzzing               weather to merge the fuzzed value with the valid value
     * @return a FuzzingResult containing the modified payload and information about the replacement
     */
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
        if (JsonUtils.isValidJson(elementToString(valueToSet))) {
            if (areBothPropertyToReplaceAndValueToReplaceArrays(jsonPropertyForReplacement, valueToSet)) {
                jsonPropertyForReplacement = removeArrayTermination(jsonPropertyForReplacement);
            }
            try {
                jsonDocument.set(JsonUtils.sanitizeToJsonPath(jsonPropertyForReplacement), JsonUtils.JSON_PERMISSIVE_PARSER.parse(String.valueOf(valueToSet)));
            } catch (ParseException e) {
                throw new CatsException(e);
            }
        } else {
            jsonDocument.set(JsonUtils.sanitizeToJsonPath(jsonPropertyForReplacement), valueToSet);
        }
    }

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
        Set<String> additionalPropertiesKeys = currentPathValues.keySet().stream().filter(
                key -> key.matches(ADDITIONAL_PROPERTIES)).collect(Collectors.toSet());
        String result = payload;

        for (String additionalPropertiesKey : additionalPropertiesKeys) {
            String additionalProperties = WordUtils.nullOrValueOf(currentPathValues.get(additionalPropertiesKey));
            if (additionalProperties != null && StringUtils.isNotBlank(result)) {
                DocumentContext jsonDoc = JsonPath.parse(result);
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

                result = jsonDoc.jsonString();
            }
        }
        return result;
    }

    private void setMapValues(DocumentContext jsonDoc, String additionalProperties, String prefix) {
        String mapValues = additionalProperties.replace(MAP_VALUES + "=", "").replace("{", "").replace("}", "");
        for (String values : mapValues.split(",", -1)) {
            String[] entry = values.split("=", -1);
            jsonDoc.put(JsonPath.compile(prefix), entry[0].trim(), CatsDSLParser.parseAndGetResult(entry[1].trim(), Map.of(Parser.REQUEST, jsonDoc.jsonString())));
        }
    }

    private static String elementToString(Object obj) {
        if (obj instanceof List<?> stringList) {
            return "[" + stringList.stream()
                    .map(element -> {
                        if (element instanceof Number) {
                            return String.valueOf(element);
                        } else {
                            return "\"" + element + "\"";
                        }
                    })
                    .collect(Collectors.joining(", ")) + "]";
        }

        return String.valueOf(obj);
    }

    /**
     * Converts the given initial value to an appropriate type, if possible.
     * <p>
     * This method checks if the initial value can be converted to a numeric type (such as Integer, Long, Double, etc.)
     * using the {@link org.apache.commons.lang3.math.NumberUtils#isCreatable(String)} method. If the conversion is
     * possible, it returns the converted numeric value; otherwise, it returns the initial value as is.
     * </p>
     *
     * @param initialValue the initial value to be converted
     * @return the converted numeric value if possible; otherwise, the initial value as a String
     * @see org.apache.commons.lang3.math.NumberUtils#isCreatable(String)
     * @see org.apache.commons.lang3.math.NumberUtils#createNumber(String)
     */
    public static Object getAsAppropriateType(String initialValue) {
        return NumberUtils.isCreatable(initialValue) ? NumberUtils.createNumber(initialValue) : initialValue;
    }

    /**
     * Checks each element in the given array against a specified predicate and constructs a comma-separated
     * string of elements that satisfy the predicate. The resulting string is stripped of leading commas and spaces.
     *
     * @param pathElements  The array of strings to be checked.
     * @param checkFunction The predicate used to test each element in the array.
     *                      Elements that satisfy the predicate will be included in the result.
     * @return A comma-separated string of elements that satisfy the predicate,
     * or {@code N_A} if no elements meet the criteria.
     */
    public static String check(String[] pathElements, Predicate<String> checkFunction) {
        StringBuilder result = new StringBuilder();

        for (String pathElement : pathElements) {
            if (checkFunction.test(pathElement)) {
                result.append(COMMA).append(pathElement);
            }
        }

        if (!result.toString().isEmpty()) {
            return StringUtils.stripStart(result.toString().trim(), ", ");
        }

        return N_A;
    }
}