package com.endava.cats.model;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Builder
@Getter
@ToString
public class FuzzingData {
    private final HttpMethod method;
    private final String path;
    private final Set<CatsHeader> headers;
    private final String payload;
    private final Set<String> responseCodes;
    private final Schema reqSchema;
    private final PathItem pathItem;
    private final Map<String, Schema> schemaMap;
    private final Map<String, List<String>> responses;
    private final Map<String, List<String>> responseContentTypes;
    private final Map<String, Schema> requestPropertyTypes;
    private final List<String> requestContentTypes;
    private final Set<String> queryParams;
    private final OpenAPI openApi;
    private final List<String> tags;
    private final String reqSchemaName;

    /*these are cached after the first computation*/
    private Set<String> allFields;
    private Set<Set<String>> allFieldsSetOfSets;
    private List<String> allRequiredFields;
    private Set<CatsField> allFieldsAsCatsFields;
    private Set<String> allReadOnlyFields;
    private Set<String> allWriteOnlyFields;
    private String processedPayload;
    private Set<String> targetFields;


    public boolean isQueryParam(String field) {
        return this.queryParams.contains(field);
    }

    public String getPayload() {
        if (processedPayload == null) {
            processedPayload = this.removeReadWrite();
        }

        return processedPayload;
    }

    private String removeReadWrite() {
        if (HttpMethod.requiresBody(method)) {
            return this.removeReadOnlyFields(this.getAllReadOnlyFields());
        }
        return this.removeReadOnlyFields(this.getAllWriteOnlyFields());
    }

    private String removeReadOnlyFields(Set<String> fieldsToRemove) {
        String result = payload;
        for (String readOnlyField : fieldsToRemove) {
            result = JsonUtils.deleteNode(result, readOnlyField);
        }

        return result;
    }

    private Set<CatsField> getFields(Schema schema, String prefix) {
        Set<CatsField> catsFields = new HashSet<>();
        if (schema.get$ref() != null) {
            schema = schemaMap.get(this.getDefinitionNameFromRef(schema.get$ref()));
        }
        List<String> required = Optional.ofNullable(schema.getRequired()).orElseGet(Collections::emptyList);

        if (schema.getProperties() != null) {
            for (Map.Entry<String, Schema> prop : (Set<Map.Entry<String, Schema>>) schema.getProperties().entrySet()) {
                catsFields.add(CatsField.builder()
                        .name(prefix.isEmpty() ? prop.getKey() : prefix + "#" + prop.getKey())
                        .schema(prop.getValue())
                        .required(required.contains(prop.getKey()))
                        .readOnly(Optional.ofNullable(prop.getValue().getReadOnly()).orElse(false))
                        .writeOnly(Optional.ofNullable(prop.getValue().getWriteOnly()).orElse(false))
                        .build());
                catsFields.addAll(this.getFields(prop.getValue(), prefix.isEmpty() ? prop.getKey() : prefix + "#" + prop.getKey()));
            }
        } else if (schema instanceof ComposedSchema) {
            ComposedSchema composedSchema = (ComposedSchema) schema;
            Optional.ofNullable(composedSchema.getAllOf()).ifPresent(allOf -> allOf.forEach(item -> catsFields.addAll(this.getFields(item, prefix))));
            Optional.ofNullable(composedSchema.getAnyOf()).ifPresent(anyOf -> anyOf.forEach(item -> catsFields.addAll(this.getFields(item, prefix))));
            Optional.ofNullable(composedSchema.getOneOf()).ifPresent(oneOf -> oneOf.forEach(item -> catsFields.addAll(this.getFields(item, prefix))));
        }

        return catsFields;
    }


    private String getDefinitionNameFromRef(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    public Set<String> getAllReadOnlyFields() {
        if (allReadOnlyFields == null) {
            allReadOnlyFields = this.getAllFieldsAsCatsFields().stream().filter(CatsField::isReadOnly).map(CatsField::getName).collect(Collectors.toSet());
        }
        return allReadOnlyFields;
    }

    public Set<String> getAllWriteOnlyFields() {
        if (allWriteOnlyFields == null) {
            allWriteOnlyFields = this.getAllFieldsAsCatsFields().stream().filter(CatsField::isWriteOnly).map(CatsField::getName).collect(Collectors.toSet());
        }
        return allWriteOnlyFields;
    }

    public List<String> getAllRequiredFields() {
        if (allRequiredFields == null) {
            allRequiredFields = this.getAllFieldsAsCatsFields().stream().filter(CatsField::isRequired).map(CatsField::getName).collect(Collectors.toList());
        }
        return allRequiredFields;
    }

    public Set<CatsField> getAllFieldsAsCatsFields() {
        if (allFieldsAsCatsFields == null) {
            allFieldsAsCatsFields = this.getFields(reqSchema, "");
        }

        return allFieldsAsCatsFields;
    }

    public Set<String> getAllFieldsByHttpMethod() {
        if (HttpMethod.requiresBody(method)) {
            return getAllFields().stream().filter(field -> !this.getAllReadOnlyFields().contains(field)).collect(Collectors.toSet());
        }

        return getAllFields().stream().filter(field -> !this.getAllWriteOnlyFields().contains(field)).collect(Collectors.toSet());
    }

    private Set<String> getAllFields() {
        if (allFields == null) {
            allFields = this.getAllFieldsAsCatsFields().stream().map(CatsField::getName).collect(Collectors.toSet());
        }

        return allFields;
    }

    public Set<Set<String>> getAllFields(SetFuzzingStrategy setFuzzingStrategy, int maxFieldsToRemove) {
        if (allFieldsSetOfSets == null) {

            Set<Set<String>> sets;
            switch (setFuzzingStrategy) {
                case POWERSET:
                    sets = SetFuzzingStrategy.powerSet(this.getAllFields());
                    break;
                case SIZE:
                    sets = SetFuzzingStrategy.getAllSetsWithMinSize(this.getAllFields(), maxFieldsToRemove);
                    break;
                default:
                    sets = SetFuzzingStrategy.removeOneByOne(this.getAllFields());
            }
            sets.remove(Collections.emptySet());
            allFieldsSetOfSets = sets;
        }
        return allFieldsSetOfSets;
    }

    public String getFirstRequestContentType() {
        return requestContentTypes.get(0);
    }

    public enum SetFuzzingStrategy {
        POWERSET, SIZE, ONEBYONE;

        private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(SetFuzzingStrategy.class);

        /**
         * Returns all possible subsets of the given set
         *
         * @param originalSet
         * @param <T>
         * @return
         */
        public static <T> Set<Set<T>> powerSet(Set<T> originalSet) {
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
        private static Set<Set<String>> getAllSubsetsOfSize(List<String> elements, int k) {
            List<List<String>> result = new ArrayList<>();
            backtrack(elements, k, 0, result, new ArrayList<>());
            return result.stream().map(HashSet::new).collect(Collectors.toSet());
        }

        private static void backtrack(List<String> elements, int k, int startIndex, List<List<String>> result,
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
        public static <T> Set<Set<T>> removeOneByOne(Set<T> elements) {
            Set<Set<T>> result = new HashSet<>();
            for (T t : elements) {
                Set<T> subset = new HashSet<>();
                subset.add(t);
                result.add(subset);
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
        public static Set<Set<String>> getAllSetsWithMinSize(Set<String> allFields, int maxFieldsToRemove) {
            Set<Set<String>> sets = new HashSet<>();
            if (maxFieldsToRemove == 0) {
                LOGGER.info("fieldsSubsetMinSize is ZERO, the value will be changed to {}", allFields.size() / 2);
                maxFieldsToRemove = allFields.size() / 2;
            } else if (allFields.size() < maxFieldsToRemove) {
                LOGGER.info("fieldsSubsetMinSize is bigger than the number of fields, the value will be changed to {}", allFields.size());
            }
            for (int i = maxFieldsToRemove; i >= 1; i--) {
                sets.addAll(getAllSubsetsOfSize(new ArrayList<>(allFields), i));
            }
            return sets;
        }
    }
}
