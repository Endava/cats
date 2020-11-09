package com.endava.cats.util;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingResult;
import com.endava.cats.model.FuzzingStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.internal.ParseContextImpl;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import net.minidev.json.JSONArray;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.endava.cats.util.CustomFuzzerUtil.*;

@Component
public class CatsUtil {
    public static final String FIRST_ELEMENT_FROM_ROOT_ARRAY = "$[0]#";
    public static final String ALL_ELEMENTS_ROOT_ARRAY = "$[*]#";

    private static final Configuration JACKSON_JSON_NODE_CONFIGURATION = Configuration.builder()
            .mappingProvider(new JacksonMappingProvider())
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .build();

    private static final ParseContext PARSE_CONTEXT = new ParseContextImpl(JACKSON_JSON_NODE_CONFIGURATION);
    private static final Logger LOGGER = LoggerFactory.getLogger(CatsUtil.class);

    private CatsDSLParser catsDSLParser;

    @Autowired
    public CatsUtil(CatsDSLParser parser) {
        this.catsDSLParser = parser;
    }

    public static <T> List<T> filterAndPrintNotMatching(Collection<T> collection, Predicate<T> predicateToFilter, Logger logger, String messageWhenNotMatching, Function<T, String> functionToApplyToLoggedItems, String... params) {
        Map<Boolean, List<T>> results = collection.stream().collect(Collectors.partitioningBy(predicateToFilter));

        List<T> notMatching = results.get(false);

        notMatching.forEach(element -> logger.info(messageWhenNotMatching, functionToApplyToLoggedItems.apply(element), params));

        return results.get(true);
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

    /**
     * Parses a Yaml file
     *
     * @param yaml
     * @return the parse Yaml as a map of maps
     * @throws IOException
     */
    public Map<String, Map<String, Object>> parseYaml(String yaml) throws IOException {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (Reader reader = new InputStreamReader(new FileInputStream(yaml), Charsets.UTF_8)) {
            JsonNode node = mapper.reader().readTree(reader);
            Map<String, Object> paths = mapper.convertValue(node, Map.class);

            for (Map.Entry<String, Object> entry : paths.entrySet()) {
                Map<String, Object> properties = mapper.convertValue(entry.getValue(), Map.class);
                result.put(entry.getKey(), properties);
            }
        }
        return result;
    }

    /**
     * Returns all the sets obtained by removing at max {@code maxFieldsToRemove}
     *
     * @param allFields
     * @param maxFieldsToRemove
     * @return
     */
    public Set<Set<String>> getAllSetsWithMinSize(Set<String> allFields, String maxFieldsToRemove) {
        Set<Set<String>> sets = new HashSet<>();
        int maxFieldsToRemoveAsInt = Integer.parseInt(maxFieldsToRemove);
        if (maxFieldsToRemoveAsInt == 0) {
            LOGGER.info("fieldsSubsetMinSize is ZERO, the value will be changed to {}", allFields.size() / 2);
            maxFieldsToRemoveAsInt = allFields.size() / 2;
        } else if (allFields.size() < maxFieldsToRemoveAsInt) {
            LOGGER.info("fieldsSubsetMinSize is bigger than the number of fields, the value will be changed to {}", allFields.size());
        }
        for (int i = maxFieldsToRemoveAsInt; i >= 1; i--) {
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
                valueToSet = FuzzingStrategy.mergeFuzzing(this.nullOrValueOf(oldValue), fuzzingStrategyToApply.getData(), "   ");
            }
            context.set(sanitizeToJsonPath(jsonPropertyForReplacement), valueToSet);

            return new FuzzingResult(context.jsonString(), fuzzingStrategyToApply.process(oldValue));
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

    public void mapObjsToString(String headersFile, Map<String, Map<String, String>> headers) throws IOException {
        Map<String, Map<String, Object>> headersAsObject = this.parseYaml(headersFile);
        for (Map.Entry<String, Map<String, Object>> entry : headersAsObject.entrySet()) {
            headers.put(entry.getKey(), entry.getValue().entrySet()
                    .stream().collect(Collectors.toMap(Map.Entry::getKey, en -> String.valueOf(en.getValue()))));
        }
    }

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