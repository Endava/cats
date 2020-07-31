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
    private final Map<String, Schema> requestPropertyTypes;
    private final CatsUtil catsUtil;
    private Set<String> allFields;
    private Set<Set<String>> allFieldsSetOfSets;
    private List<String> allRequiredFields;
    private final Set<String> queryParams;

    public Map<String, Schema> getAllProperties() {
        if (this.reqSchema.getProperties() != null) {
            return this.reqSchema.getProperties();
        }
        Map<String, Schema> properties = new HashMap<>();
        if (this.reqSchema instanceof ComposedSchema) {
            properties = this.processComposedSchemaForAllFields();
        }
        return properties;
    }

    private Map<String, Schema> processComposedSchemaForAllFields() {
        Map<String, Schema> properties = new HashMap<>();
        List<Schema> allOf = ((ComposedSchema) this.reqSchema).getAllOf();
        if (allOf != null) {
            for (Schema schema : allOf) {
                if (schema.getProperties() != null) {
                    properties.putAll(schema.getProperties());
                } else {
                    String refName = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
                    Schema actualSchema = schemaMap.get(refName);
                    properties.putAll(actualSchema.getProperties());
                }
            }
        }
        return properties;
    }

    public List<String> getAllRequiredFields() {
        if (allRequiredFields == null) {
            if (reqSchema.getProperties() != null) {
                allRequiredFields = this.getAllRequiredFields(this.reqSchema, this.schemaMap);
            } else {
                allRequiredFields = new ArrayList<>();

                List<String> composedRequiredFields = new ArrayList<>();
                if (this.reqSchema instanceof ComposedSchema) {
                    composedRequiredFields = this.processComposedSchemaForRequiredFields();
                }
                allRequiredFields.addAll(composedRequiredFields);
            }

        }
        return allRequiredFields;
    }

    private List<String> processComposedSchemaForRequiredFields() {
        List<String> requiredProperties = new ArrayList<>();
        List<Schema> allOf = ((ComposedSchema) this.reqSchema).getAllOf();
        if (allOf != null) {
            for (Schema schema : allOf) {
                if (schema.get$ref() != null) {
                    schema = schemaMap.get(catsUtil.getDefinitionNameFromRef(schema.get$ref()).orElse(""));
                }
                requiredProperties.addAll(this.getAllRequiredFields(schema, schemaMap));
            }
        }

        return requiredProperties;
    }

    public Set<String> getAllFieldsAsSingleSet() {
        if (allFields == null) {
            Set<Set<String>> sets = this.getAllFields(SetFuzzingStrategy.ONEBYONE, "");
            allFields = sets.stream().map(set -> set.iterator().next()).collect(Collectors.toSet());
        }

        return allFields;
    }

    public Set<Set<String>> getAllFields(SetFuzzingStrategy setFuzzingStrategy, String maxFieldsToRemove) {
        if (allFieldsSetOfSets == null) {
            allFields = catsUtil.getAllFields(this);

            Set<Set<String>> sets;
            switch (setFuzzingStrategy) {
                case POWERSET:
                    sets = catsUtil.powerSet(allFields);
                    break;
                case SIZE:
                    sets = catsUtil.getAllSetsWithMinSize(allFields, maxFieldsToRemove);
                    break;
                default:
                    sets = catsUtil.removeOneByOne(allFields);
            }
            sets.remove(Collections.emptySet());
            allFieldsSetOfSets = sets;
        }
        return allFieldsSetOfSets;
    }

    private List<String> getAllRequiredFields(Schema currentSchema, Map<String, Schema> schemaMap) {
        Set<String> requiredSubfields = catsUtil.eliminateStartingCharAndHacks(this.getAllRequiredSubfields(currentSchema, "", schemaMap));
        List<String> requiredFields = new ArrayList<>(Optional.ofNullable(currentSchema.getRequired()).orElse(Collections.emptyList()));
        requiredFields.addAll(requiredSubfields);

        return requiredFields;
    }

    private Set<String> getAllRequiredSubfields(Schema currentSchema, String prefix, Map<String, Schema> schemaMap) {
        Set<String> result = new HashSet<>();
        if (currentSchema.getProperties() != null) {
            for (Map.Entry<String, Schema> prop : (Set<Map.Entry<String, Schema>>) currentSchema.getProperties().entrySet()) {
                result.addAll(this.getAllRequiredSubfieldsAsFullyQualifiedNames(prefix, schemaMap, prop));
            }
        }
        return result;
    }

    private Set<String> getAllRequiredSubfieldsAsFullyQualifiedNames(String prefix, Map<String, Schema> schemaMap, Map.Entry<String, Schema> currentProperty) {
        Set<String> result = new HashSet<>();
        Schema newSchema = catsUtil.getDefinitionNameFromRef(currentProperty.getValue().get$ref()).map(schemaMap::get).orElse(currentProperty.getValue());

        if (newSchema.getRequired() != null) {
            String qualifiedPrefix = prefixWithQualifier(prefix, currentProperty.getKey());
            List<String> list = (List<String>) newSchema.getRequired().stream().map(field -> prefixWithQualifier(qualifiedPrefix, field)).collect(Collectors.toList());
            result.addAll(list);
            result.addAll(this.getAllRequiredSubfields(newSchema, prefixWithQualifier(prefix, currentProperty.getKey()), schemaMap));
        }

        return result;
    }

    private String prefixWithQualifier(String qualifiedPrefix, Object field) {
        return qualifiedPrefix + "#" + field;
    }

    public enum SetFuzzingStrategy {
        POWERSET, SIZE, ONEBYONE
    }
}
