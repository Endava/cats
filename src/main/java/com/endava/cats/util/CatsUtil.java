package com.endava.cats.util;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingResult;
import com.endava.cats.model.FuzzingStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.internal.ParseContextImpl;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logmanager.LogContext;

import javax.enterprise.context.ApplicationScoped;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.endava.cats.util.CustomFuzzerUtil.ADDITIONAL_PROPERTIES;
import static com.endava.cats.util.CustomFuzzerUtil.ELEMENT;
import static com.endava.cats.util.CustomFuzzerUtil.MAP_VALUES;

@ApplicationScoped
public class CatsUtil {
    public static final String FIRST_ELEMENT_FROM_ROOT_ARRAY = "$[0]#";
    public static final String ALL_ELEMENTS_ROOT_ARRAY = "$[*]#";
    public static final String FUZZER_KEY_DEFAULT = "*******";
    public static final String TEST_KEY_DEFAULT = "**********";

    private static final Configuration JACKSON_JSON_NODE_CONFIGURATION = Configuration.builder()
            .mappingProvider(new JacksonMappingProvider())
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .build();
    private static final ParseContext PARSE_CONTEXT = new ParseContextImpl(JACKSON_JSON_NODE_CONFIGURATION);
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(CatsUtil.class);
    private final List<String> spacesHeaders = Arrays.asList(" ", "\u0009", "\r");

    private final List<String> whitespacesHeaders = Arrays.asList(
            "\u1680", "\u2000", "\u2001", "\u2002", "\u2003", "\u2004", "\u2005", "\u2006", "\u2007", "\u2008", "\u2009",
            "\u200A", "\u2028", "\u2029", "\u202F", "\u205F", "\u3000", "\u00A0");

    private final List<String> whitespacesFields = Arrays.asList(
            " ", "\u1680", "\u2000", "\u2001", "\u2002", "\u2003", "\u2004", "\u2005", "\u2006",
            "\u2007", "\u2008", "\u2009", "\u200A", "\u2028", "\u2029", "\u202F", "\u205F", "\u3000", "\u00A0");

    private final List<String> controlCharsHeaders = Arrays.asList(
            "\r\n", "\u0000", "\u0007", "\u0008", "\n", "\u000B", "\u000C", "\r", "\u200B", "\u200C", "\u200D", "\u200E",
            "\u200F", "\u202A", "\u202B", "\u202C", "\u202D", "\u202E", "\u2060", "\u2061", "\u2062", "\u2063", "\u2064", "\u206D", "\u0015",
            "\u0016", "\u0017", "\u0018", "\u0019", "\u001A", "\u001B", "\u001C", "\u001D", "\u001E", "\u001F", "\u007F", "\u0080", "\u0081",
            "\u0082", "\u0083", "\u0085", "\u0086", "\u0087", "\u0088", "\u008A", "\u008B", "\u008C", "\u008D", "\u0090", "\u0091", "\u0093",
            "\u0094", "\u0095", "\u0096", "\u0097", "\u0098", "\u0099", "\u009A", "\u009B", "\u009C", "\u009D", "\u009E", "\u009F", "\uFEFF", "\uFFFE", "\u00AD");

    private final List<String> controlCharsFields = Arrays.asList(
            "\r\n", "\u0007", "\u0008", "\u0009", "\n", "\u000B", "\u000C", "\r", "\u200B", "\u200C", "\u200D", "\u200E",
            "\u200F", "\u202A", "\u202B", "\u202C", "\u202D", "\u202E", "\u2060", "\u2061", "\u2062", "\u2063", "\u2064", "\u206D",
            "\u0015", "\u0016", "\u0017", "\u0018", "\u0019", "\u001A", "\u001B", "\u001C", "\u001D", "\u001E", "\u001F", "\u007F",
            "\u0080", "\u0081", "\u0082", "\u0083", "\u0085", "\u0086", "\u0087", "\u0088", "\u008A", "\u008B", "\u008C", "\u008D",
            "\u0090", "\u0091", "\u0093", "\u0094", "\u0095", "\u0096", "\u0097", "\u0098", "\u0099", "\u009A", "\u009B", "\u009C",
            "\u009D", "\u009E", "\u009F", "\uFEFF", "\uFFFE", "\u00AD");

    private final List<String> singleCodePointEmojis = Arrays.asList("\uD83E\uDD76", "\uD83D\uDC80", "\uD83D\uDC7B", "\uD83D\uDC7E");

    private final List<String> multiCodePointEmojis = Arrays.asList("\uD83D\uDC69\uD83C\uDFFE", "\uD83D\uDC68\u200D\uD83C\uDFED️", "\uD83D\uDC69\u200D\uD83D\uDE80");

    private final CatsDSLParser catsDSLParser;

    public CatsUtil(CatsDSLParser parser) {
        this.catsDSLParser = parser;
    }

    public static <T> List<T> filterAndPrintNotMatching(Collection<T> collection, Predicate<T> predicateToFilter, PrettyLogger logger, String messageWhenNotMatching, Function<T, String> functionToApplyToLoggedItems, String... params) {
        Map<Boolean, List<T>> results = collection.stream().collect(Collectors.partitioningBy(predicateToFilter));

        List<T> notMatching = results.get(false);

        notMatching.forEach(element -> logger.skip(messageWhenNotMatching, functionToApplyToLoggedItems.apply(element), params));

        return results.get(true);
    }

    public static OpenAPI readOpenApi(String location) throws IOException {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);

        if (location.startsWith("http")) {
            return openAPIV3Parser.readLocation(location, null, options).getOpenAPI();
        } else {
            return openAPIV3Parser.readContents(Files.readString(Paths.get(location)), null, options).getOpenAPI();
        }
    }

    public static String markLargeString(String input) {
        return "ca" + input + "ts";
    }

    public static boolean isArgumentValid(List<String> argument) {
        return argument != null && !argument.isEmpty();
    }

    public static List<FuzzingStrategy> getLargeValuesStrategy(int largeStringsSize) {
        String generatedValue = StringGenerator.generateRandomUnicode();
        int payloadSize = largeStringsSize / generatedValue.length();
        if (payloadSize == 0) {
            return Collections.singletonList(FuzzingStrategy.replace().withData(CatsUtil.markLargeString(generatedValue.substring(0, largeStringsSize))));
        }
        return Collections.singletonList(FuzzingStrategy.replace().withData(CatsUtil.markLargeString(StringUtils.repeat(generatedValue, payloadSize + 1))));
    }

    public static void setCatsLogLevel(String level) {
        setLogLevel("com.endava.cats", level);
    }

    public static void setLogLevel(String pkg, String level) {
        LogContext.getLogContext().getLogger(pkg).setLevel(Level.parse(level.toUpperCase(Locale.ROOT)));
    }

    public List<String> getControlCharsFields() {
        return this.controlCharsFields;
    }

    public List<String> getControlCharsHeaders() {
        return this.controlCharsHeaders;
    }

    public List<String> getSeparatorsFields() {
        return this.whitespacesFields;
    }

    public List<String> getSeparatorsHeaders() {
        return this.whitespacesHeaders;
    }

    public List<String> getSpacesHeaders() {
        return this.spacesHeaders;
    }

    public List<String> getSingleCodePointEmojis() {
        return this.singleCodePointEmojis;
    }

    public List<String> getMultiCodePointEmojis() {
        return this.multiCodePointEmojis;
    }

    /**
     * Returns all possible subsets of the given set
     *
     * @param originalSet
     * @param <T>
     * @return
     */
    public <T> Set<Set<T>> powerSet(Set<T> originalSet) {
        Set<Set<T>> sets = new HashSet<>();
        if (originalSet.isEmpty()) {
            sets.add(new HashSet<>());
            return sets;
        }
        List<T> list = new ArrayList<>(originalSet);
        T head = list.get(0);
        Set<T> rest = new HashSet<>(list.subList(1, list.size()));
        for (Set<T> set : powerSet(rest)) {
            Set<T> newSet = new HashSet<>();
            newSet.add(head);
            newSet.addAll(set);
            sets.add(newSet);
            sets.add(set);
        }
        return sets;
    }

    /**
     * Returns all possible subsets of size K of the given list
     *
     * @param elements
     * @param k
     * @return
     */
    private Set<Set<String>> getAllSubsetsOfSize(List<String> elements, int k) {
        List<List<String>> result = new ArrayList<>();
        backtrack(elements, k, 0, result, new ArrayList<>());
        return result.stream().map(HashSet::new).collect(Collectors.toSet());
    }

    private void backtrack(List<String> elements, int k, int startIndex, List<List<String>> result,
                           List<String> partialList) {
        if (k == partialList.size()) {
            result.add(new ArrayList<>(partialList));
            return;
        }
        for (int i = startIndex; i < elements.size(); i++) {
            partialList.add(elements.get(i));
            backtrack(elements, k, i + 1, result, partialList);
            partialList.remove(partialList.size() - 1);
        }
    }

    /**
     * Returns a Set of sets with possibilities of removing one field at a time form the original set.
     *
     * @param elements a given Set
     * @param <T>      the type of the elements
     * @return a Set of incremental Sets obtained by removing one element at a time from the original Set
     */
    public <T> Set<Set<T>> removeOneByOne(Set<T> elements) {
        Set<Set<T>> result = new HashSet<>();
        for (T t : elements) {
            Set<T> subset = new HashSet<>();
            subset.add(t);
            result.add(subset);
        }
        return result;
    }

    public Map<String, Map<String, Object>> parseYaml(String yaml) throws IOException {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (Reader reader = new InputStreamReader(new FileInputStream(yaml), StandardCharsets.UTF_8)) {
            JsonNode node = mapper.reader().readTree(reader);
            Map<String, Object> paths = mapper.convertValue(node, Map.class);

            for (Map.Entry<String, Object> entry : paths.entrySet()) {
                Map<String, Object> properties = mapper.convertValue(entry.getValue(), LinkedHashMap.class);
                result.put(entry.getKey(), properties);
            }
        }
        return result;
    }

    /**
     * Returns all the sets obtained by removing at max {@code maxFieldsToRemove}
     *
     * @param allFields         all fields from the request, including fully qualified fields
     * @param maxFieldsToRemove number of max fields to remove
     * @return a Set of Sets with all fields combinations
     */
    public Set<Set<String>> getAllSetsWithMinSize(Set<String> allFields, int maxFieldsToRemove) {
        Set<Set<String>> sets = new HashSet<>();
        if (maxFieldsToRemove == 0) {
            LOGGER.info("fieldsSubsetMinSize is ZERO, the value will be changed to {}", allFields.size() / 2);
            maxFieldsToRemove = allFields.size() / 2;
        } else if (allFields.size() < maxFieldsToRemove) {
            LOGGER.info("fieldsSubsetMinSize is bigger than the number of fields, the value will be changed to {}", allFields.size());
        }
        for (int i = maxFieldsToRemove; i >= 1; i--) {
            sets.addAll(this.getAllSubsetsOfSize(new ArrayList<>(allFields), i));
        }
        return sets;
    }

    public boolean isPrimitive(String payload, String property) {
        if (isJsonArray(payload)) {
            property = FIRST_ELEMENT_FROM_ROOT_ARRAY + property;
        }
        try {
            JsonNode jsonNode = PARSE_CONTEXT.parse(payload).read(sanitizeToJsonPath(property));
            return jsonNode.isValueNode();
        } catch (PathNotFoundException e) {
            return false;
        }
    }

    public boolean isJsonArray(String payload) {
        return JsonPath.parse(payload).read("$") instanceof JSONArray;
    }

    public FuzzingResult replaceField(String payload, String jsonPropertyForReplacement, FuzzingStrategy fuzzingStrategyToApply) {
        return this.replaceField(payload, jsonPropertyForReplacement, fuzzingStrategyToApply, false);
    }

    public String deleteNode(String payload, String node) {
        if (StringUtils.isNotBlank(payload)) {
            try {
                return JsonPath.parse(payload).delete(this.sanitizeToJsonPath(node)).jsonString();
            } catch (PathNotFoundException e) {
                return payload;
            }
        }
        return payload;
    }

    public FuzzingResult replaceField(String payload, String jsonPropertyForReplacement, FuzzingStrategy fuzzingStrategyToApply, boolean mergeFuzzing) {
        if (StringUtils.isNotBlank(payload)) {
            String jsonPropToGetValue = jsonPropertyForReplacement;
            if (isJsonArray(payload)) {
                jsonPropToGetValue = FIRST_ELEMENT_FROM_ROOT_ARRAY + jsonPropertyForReplacement;
                jsonPropertyForReplacement = ALL_ELEMENTS_ROOT_ARRAY + jsonPropertyForReplacement;
            }
            DocumentContext context = JsonPath.parse(payload);
            Object oldValue = context.read(sanitizeToJsonPath(jsonPropToGetValue));
            String valueToSet = fuzzingStrategyToApply.process(oldValue);
            if (mergeFuzzing) {
                valueToSet = FuzzingStrategy.mergeFuzzing(this.nullOrValueOf(oldValue), fuzzingStrategyToApply.getData());
            }
            context.set(sanitizeToJsonPath(jsonPropertyForReplacement), valueToSet);

            return new FuzzingResult(context.jsonString(), valueToSet);
        }
        return FuzzingResult.empty();
    }

    private String nullOrValueOf(Object object) {
        return object == null ? null : String.valueOf(object);
    }

    public boolean isValidJson(String text) {
        try {
            JsonParser.parseString(text);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public Object[] getExpectedWordingBasedOnRequiredFields(boolean anyMandatory) {
        return new Object[]{
                this.getResultCodeBasedOnRequiredFieldsRemoved(anyMandatory).asString(),
                anyMandatory ? "were" : "were not"
        };
    }

    public ResponseCodeFamily getResultCodeBasedOnRequiredFieldsRemoved(boolean required) {
        return required ? ResponseCodeFamily.FOURXX : ResponseCodeFamily.TWOXX;
    }

    public boolean isHttpMethodWithPayload(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    public JsonElement parseAsJsonElement(String payload) {
        JsonReader reader = new JsonReader(new StringReader(payload));
        reader.setLenient(true);
        return JsonParser.parseReader(reader);
    }

    public void loadFileToMap(String headersFile, Map<String, Map<String, String>> headers) throws IOException {
        Map<String, Map<String, Object>> headersAsObject = this.parseYaml(headersFile);
        for (Map.Entry<String, Map<String, Object>> entry : headersAsObject.entrySet()) {
            headers.put(entry.getKey(), entry.getValue().entrySet()
                    .stream().collect(Collectors.toMap(Map.Entry::getKey, en -> String.valueOf(en.getValue()))));
        }
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
            jsonDoc.put(JsonPath.compile(prefix), entry[0].trim(), catsDSLParser.parseAndGetResult(entry[1].trim(), jsonDoc.jsonString()));
        }
    }

    public String sanitizeToJsonPath(String input) {
        return input.replace("#", ".");
    }

    public boolean equalAsJson(String json1, String json2) {
        return JsonPath.parse(json1).jsonString().contentEquals(JsonPath.parse(json2).jsonString());
    }
}