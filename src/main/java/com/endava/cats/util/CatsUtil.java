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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
public class CatsUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatsUtil.class);

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
     * Parses the Ref data yaml
     *
     * @param yaml
     * @return
     * @throws IOException
     */
    public Map<String, Map<String, Object>> parseYaml(String yaml) throws IOException {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode node = mapper.reader().readTree(new FileReader(yaml));
        Map<String, Object> paths = mapper.convertValue(node, Map.class);

        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            Map<String, Object> properties = mapper.convertValue(entry.getValue(), Map.class);
            result.put(entry.getKey(), properties);
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
    public Set<Set<String>> getAllSetsWithMinSize(Set allFields, String maxFieldsToRemove) {
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
        JsonElement jsonElement = JsonParser.parseString(payload);

        if (jsonElement.isJsonObject()) {
            return isPrimitive(property, jsonElement);
        } else {
            return false;
        }
    }

    /**
     * Check if the fully qualified supplied property is a primitive
     *
     * @param property a fully qualified property in format foo#cats#op
     * @param element
     * @return
     */
    private boolean isPrimitive(String property, JsonElement element) {
        String[] propertyAsArray = property.split("#");
        for (int i = 0; i < propertyAsArray.length - 1; i++) {
            if (element != null) {
                element = element.getAsJsonObject().get(propertyAsArray[i]);
            }
        }
        if (element != null) {
            String lastPropertyInArray = propertyAsArray[propertyAsArray.length - 1];
            JsonElement lastPropertyAsJsonElement = element.getAsJsonObject().get(lastPropertyInArray);

            return lastPropertyAsJsonElement != null && lastPropertyAsJsonElement.isJsonPrimitive();
        }
        return false;
    }

    public FuzzingResult replaceFieldWithFuzzedValue(String payload, String jsonProperty, FuzzingStrategy valueToSet) {
        JsonElement jsonElement = JsonParser.parseString(payload);
        String fuzzedValue = "";

        if (jsonElement.isJsonObject()) {
            fuzzedValue = replaceValue(jsonProperty, jsonElement, valueToSet);
        } else if (jsonElement.isJsonArray()) {
            for (JsonElement element : jsonElement.getAsJsonArray()) {
                fuzzedValue = replaceValue(jsonProperty, element, valueToSet);
            }
        }

        return new FuzzingResult(jsonElement, fuzzedValue);
    }

    /**
     * Returns the value replaced processed by the FuzzStrategy
     *
     * @param field
     * @param jsonElement
     * @param valueToSet
     * @return
     */
    private String replaceValue(String field, JsonElement jsonElement, FuzzingStrategy valueToSet) {
        String result = "";
        String[] depth = field.split("#");
        JsonElement element = this.getJsonElementBasedOnFullyQualifiedName(jsonElement, field);

        if (element != null) {
            String propertyToReplace = depth[depth.length - 1];
            JsonElement elementToReplace = element.getAsJsonObject().get(propertyToReplace);
            String oldValue = "{}";
            if (elementToReplace.isJsonPrimitive()) {
                oldValue = elementToReplace.getAsString();
            }

            element.getAsJsonObject().remove(propertyToReplace);
            if (elementToReplace.isJsonPrimitive()) {
                result = valueToSet.process(oldValue);
                element.getAsJsonObject().addProperty(propertyToReplace, result);
            }
        }
        return result;
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

    public JsonElement getJsonElementBasedOnFullyQualifiedName(JsonElement rootElement, String fullyQualifiedName) {
        JsonElement resultElement = rootElement;
        String[] depth = fullyQualifiedName.split("#");
        for (int i = 0; i < depth.length - 1; i++) {
            if (resultElement != null) {
                resultElement = resultElement.getAsJsonObject().get(depth[i]);
            }
        }
        if (depth.length == 1 && resultElement != null && resultElement.getAsJsonObject().get(fullyQualifiedName) == null) {
            resultElement = null;
        }

        return resultElement;
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
}