package com.endava.cats.util;

import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.dsl.api.Parser;
import com.endava.cats.exception.CatsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.javafaker.Faker;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import net.minidev.json.parser.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jboss.logmanager.LogContext;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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
public abstract class CatsUtil {
    private static final String COMMA = ", ";
    private static final String N_A = "N/A";

    private static final Faker FAKER = new Faker(random());

    private CatsUtil() {
        //ntd
    }

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
    public static void writeToYaml(String yaml, Map<String, Map<String, Object>> data) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.writeValue(new File(yaml), data);
    }


    /**
     * This method replaces the existing value of the {@code jsonPropertyForReplacement} with the supplied value.
     * For complex replacement like merging with refData values or processing the FuzzingStrategy use the {@code FuzzingStrategy.replaceField} method.
     *
     * @param payload                    the JSON payload
     * @param jsonPropertyForReplacement the JSON property path to replace
     * @param with                       the value to replace with
     * @return a result with the payload replaced
     */
    public static FuzzingResult justReplaceField(String payload, String jsonPropertyForReplacement, Object with) {
        if (JsonUtils.isJsonArray(payload)) {
            jsonPropertyForReplacement = JsonUtils.ALL_ELEMENTS_ROOT_ARRAY + jsonPropertyForReplacement;
        }
        DocumentContext jsonDocument = JsonPath.parse(payload);
        replaceOldValueWithNewOne(jsonPropertyForReplacement, jsonDocument, with);

        return new FuzzingResult(jsonDocument.jsonString(), with);
    }

    public static void replaceOldValueWithNewOne(String jsonPropertyForReplacement, DocumentContext jsonDocument, Object valueToSet) {
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
    public static String setAdditionalPropertiesToPayload(Map<String, Object> currentPathValues, String payload) {
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

    private static void setMapValues(DocumentContext jsonDoc, String additionalProperties, String prefix) {
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

    /**
     * Selects a random element from the provided iterable.
     *
     * @param iterable and collection of elements
     * @param <T>      the type of elements inside the collection
     * @return a random element from the collection
     */
    public static <T> T selectRandom(Iterable<T> iterable) {
        T selected = null;
        int count = 0;

        for (T element : iterable) {
            if (random().nextInt(++count) == 0) {
                selected = element;
            }
        }

        return selected;
    }

    /**
     * Returns a Random for random generation.
     *
     * @return a Random
     */
    public static Random random() {
        return ThreadLocalRandom.current();
    }

    /**
     * Returns a shared Faker instance for valid fake data.
     *
     * @return a common shared Faker instance
     */
    public static Faker faker() {
        return FAKER;
    }

    /**
     * Checks if a given string represents a valid URL address.
     *
     * @param urlString The string to be checked.
     * @return True if the string represents a valid URL, false otherwise.
     */
    public static boolean isValidURL(String urlString) {
        try {
            URI.create(urlString).toURL();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if a given file is empty.
     *
     * @param file The file to be checked.
     * @return True if the file is empty, false otherwise.
     */
    public static boolean isFileEmpty(File file) {
        if (file != null && file.isFile()) {
            return file.length() == 0;
        }
        return true;
    }

    /**
     * Inserts a specified string into the middle of another string.
     *
     * @param value                The original string where the insertion is performed.
     * @param whatToInsert         The string to be inserted into the middle of the original string.
     * @param insertWithoutReplace If true, the insertion is done without replacing any characters; if false,
     *                             the insertion replaces a portion of the original string.
     * @return The string resulting from the insertion operation.
     * @throws NullPointerException If 'value' or 'whatToInsert' is null.
     */
    public static String insertInTheMiddle(String value, String whatToInsert, boolean insertWithoutReplace) {
        int position = value.length() / 2;
        int whatToInsertLength = Math.min(value.length(), whatToInsert.length());
        int diffBasedOnReplace = insertWithoutReplace ? 0 : whatToInsertLength / 2;

        return value.substring(0, position - diffBasedOnReplace) + whatToInsert + value.substring(position + diffBasedOnReplace);
    }

    /**
     * Returns the maximum number of array elements that can be generated based on the length of the field value.
     *
     * @param fieldValue        The field value to be used for calculating the maximum number of array elements.
     * @param maxSizeFromSchema The maximum size of the array as defined in the schema.
     * @return The maximum number of array elements that can be generated.
     */
    public static int getMaxArraySizeBasedOnFieldsLength(String fieldValue, int maxSizeFromSchema) {
        int maxArrayLength = Integer.MAX_VALUE;
        int fieldLength = fieldValue.length();
        int maxRepetitions = (maxArrayLength + 1) / (fieldLength + 1);

        return Math.min(maxSizeFromSchema + 10, maxRepetitions);
    }
}