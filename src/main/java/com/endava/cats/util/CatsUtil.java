package com.endava.cats.util;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingResult;
import com.endava.cats.model.FuzzingStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import io.swagger.v3.oas.models.media.Schema;
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
    public static final String CATS_REQUIRED = "CATS_REQUIRED";
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
     * Returns all possible subets of size K of the given list
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
        Map<String, Map<String, Object>> result = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode node = mapper.reader().readTree(new FileReader(yaml));
        Map<String, Object> paths = mapper.convertValue(node, Map.class);

        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            Map<String, Object> properties = mapper.convertValue(entry.getValue(), Map.class);
            result.put(entry.getKey(), properties);
        }

        Map<String, Map<String, String>> resultAsString = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : result.entrySet()) {
            resultAsString.put(entry.getKey(), entry.getValue().entrySet()
                    .stream().collect(Collectors.toMap(Map.Entry::getKey, en -> String.valueOf(en.getValue()))));
        }
        return result;
    }

    /**
     * When dealing with GET or other similar request that accepts only query params we do a hack by marking the required params as CATS_REQUIRED in their corresponding schema.
     * We make sure we eliminate this hack using this method.
     *
     * @param set
     * @return
     */
    public Set<String> eliminateStartingCharAndHacks(Set<String> set) {
        return set.stream().map(item -> item.substring(1)).map(item -> item.replaceAll("#" + CATS_REQUIRED, "")).collect(Collectors.toSet());
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

    public Set getAllFields(FuzzingData data) {
        Set<String> subfields = this.eliminateStartingCharAndHacks(this.getAllSubfieldsAsFullyQualifiedNames(data.getReqSchema(), "", data.getSchemaMap()));
        Set allFields = new HashSet<>(data.getAllProperties().keySet());
        allFields.addAll(subfields);

        return allFields;
    }

    private Set<String> getAllSubfieldsAsFullyQualifiedNames(Schema currentSchema, String prefix, Map<String, Schema> schemaMap) {
        Set<String> result = new HashSet<>();
        if (currentSchema.getProperties() != null) {
            for (Map.Entry<String, Schema> prop : (Set<Map.Entry<String, Schema>>) currentSchema.getProperties().entrySet()) {
                Optional<String> propSchemaFromRef = this.getDefinitionNameFromRef(prop.getValue().get$ref());
                if (propSchemaFromRef.isPresent()) {
                    Schema newSchema = schemaMap.get(propSchemaFromRef.get());
                    result.addAll(this.getAllSubfieldsAsFullyQualifiedNames(newSchema, prefix + "#" + prop.getKey(), schemaMap));
                } else {
                    result.add(prefix + "#" + prop.getKey());
                }
            }
        }
        return result;
    }

    public boolean isPrimitive(String payload, String property) {
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(payload);

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

            return lastPropertyAsJsonElement.isJsonPrimitive();
        }
        return false;
    }

    public FuzzingResult replaceFieldWithFuzzedValue(String payload, String jsonProperty, FuzzingStrategy valueToSet) {
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(payload);
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

    public Optional<String> getDefinitionNameFromRef(String ref) {
        if (ref == null) {
            return Optional.empty();
        }

        return Optional.of(ref.substring(ref.lastIndexOf('/') + 1));
    }

    public boolean isValidJson(String text) {
        try {
            new JsonParser().parse(text);
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
        JsonElement element = rootElement;
        String[] depth = fullyQualifiedName.split("#");
        for (int i = 0; i < depth.length - 1; i++) {
            if (element != null) {
                element = element.getAsJsonObject().get(depth[i]);
            }
        }
        return element;
    }

    public JsonElement parseAsJsonElement(String payload) {
        JsonReader reader = new JsonReader(new StringReader(payload));
        reader.setLenient(true);
        JsonParser parser = new JsonParser();
        return parser.parse(reader);
    }
}