package com.endava.cats.model;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.*;
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
    private final CatsUtil catsUtil;
    private final Set<String> queryParams;
    private Set<String> allFields;
    private Set<Set<String>> allFieldsSetOfSets;
    private List<String> allRequiredFields;
    private Set<CatsField> allFieldsAsCatsFields;

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

    public Set<String> getAllFields() {
        if (allFields == null) {
            allFields = this.getAllFieldsAsCatsFields().stream().map(CatsField::getName).collect(Collectors.toSet());
        }

        return allFields;
    }

    public Set<Set<String>> getAllFields(SetFuzzingStrategy setFuzzingStrategy, String maxFieldsToRemove) {
        if (allFieldsSetOfSets == null) {

            Set<Set<String>> sets;
            switch (setFuzzingStrategy) {
                case POWERSET:
                    sets = catsUtil.powerSet(this.getAllFields());
                    break;
                case SIZE:
                    sets = catsUtil.getAllSetsWithMinSize(this.getAllFields(), maxFieldsToRemove);
                    break;
                default:
                    sets = catsUtil.removeOneByOne(this.getAllFields());
            }
            sets.remove(Collections.emptySet());
            allFieldsSetOfSets = sets;
        }
        return allFieldsSetOfSets;
    }

    public enum SetFuzzingStrategy {
        POWERSET, SIZE, ONEBYONE
    }
}
