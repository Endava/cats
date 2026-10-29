package com.endava.cats.report;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.WordUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ResponseSchemaValidator {
    private static final String SCHEMAS_URI = "https://cats.invalid/schemas/";
    private static final String COMPONENTS_REF_PREFIX = "#/components/schemas/";

    private final IdentityHashMap<OpenAPI, DocumentSchemas> documentCaches = new IdentityHashMap<>();

    private record DocumentSchemas(ObjectMapper mapper, SchemaRegistry registry,
                                   IdentityHashMap<Schema<?>, com.networknt.schema.Schema> compiledByIdentity,
                                   Map<String, com.networknt.schema.Schema> compiledByKey,
                                   Map<String, Set<String>> childrenByParent) {
    }

    ValidationResult validate(CatsResponse response, FuzzingData data) {
        if (data == null || data.getOpenApi() == null) {
            return ValidationResult.notPerformed();
        }
        Optional<Schema<?>> responseSchema = data.getResponseSchema(response.responseCodeAsString(),
                response.responseCodeAsResponseRange(), response.getResponseContentType());
        if (responseSchema.isEmpty()) {
            return ValidationResult.notPerformed();
        }

        try {
            com.networknt.schema.Schema compiledSchema = compiledSchema(responseSchema.get(), data);
            List<String> errors = compiledSchema.validate(response.getBody(), InputFormat.JSON,
                            executionContext -> executionContext.executionConfig(config -> config.formatAssertionsEnabled(true)))
                    .stream()
                    .map(this::formatError)
                    .distinct()
                    .toList();
            return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
        } catch (RuntimeException e) {
            return ValidationResult.invalid(List.of("Response schema validation failed: " + e.getMessage()));
        }
    }

    private com.networknt.schema.Schema compiledSchema(Schema<?> schema, FuzzingData data) {
        OpenAPI openAPI = data.getOpenApi();
        DocumentSchemas docSchemas = documentCaches.computeIfAbsent(openAPI, ignored -> {
            boolean openApi31 = openAPI.getSpecVersion() == SpecVersion.V31;
            ObjectMapper mapper = openApi31 ? Json31.mapper() : Json.mapper();
            Map<String, Set<String>> childrenByParent = CatsModelUtils.findAllOfChildrenByParent(data.getSchemaMap());
            SchemaRegistry registry = SchemaRegistry.withDialect(openApi31
                            ? Dialects.getOpenApi31() : Dialects.getOpenApi30(),
                    builder -> builder.schemas(schemaDocuments(mapper, data.getSchemaMap(), SCHEMAS_URI, childrenByParent))
                            .schemaLoader(loader -> loader.fetchRemoteResources(false)));
            return new DocumentSchemas(mapper, registry, new IdentityHashMap<>(), new LinkedHashMap<>(), childrenByParent);
        });
        com.networknt.schema.Schema byIdentity = docSchemas.compiledByIdentity().get(schema);
        if (byIdentity != null) {
            return byIdentity;
        }
        String refKey = schema.get$ref() != null && schema.get$ref().startsWith(COMPONENTS_REF_PREFIX)
                ? "$ref:" + CatsModelUtils.getSimpleRef(schema.get$ref())
                : null;
        if (refKey != null) {
            com.networknt.schema.Schema cached = docSchemas.compiledByKey().get(refKey);
            if (cached != null) {
                docSchemas.compiledByIdentity().put(schema, cached);
                return cached;
            }
        }
        JsonNode serializedSchema = docSchemas.mapper().valueToTree(schema);
        if (!(serializedSchema instanceof ObjectNode schemaDocument)) {
            throw new IllegalArgumentException("OpenAPI response schema is not an object");
        }
        ObjectNode root = schemaDocument.deepCopy();
        rewriteSchemaReferences(root, COMPONENTS_REF_PREFIX, SCHEMAS_URI, docSchemas.childrenByParent());
        try {
            String serializedRoot = docSchemas.mapper().writeValueAsString(root);
            String cacheKey = refKey != null ? refKey : serializedRoot;
            com.networknt.schema.Schema compiled = docSchemas.compiledByKey().computeIfAbsent(cacheKey, ignored -> {
                com.networknt.schema.Schema created = docSchemas.registry().getSchema(serializedRoot);
                created.initializeValidators();
                return created;
            });
            docSchemas.compiledByIdentity().put(schema, compiled);
            return compiled;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to serialize OpenAPI response schema", e);
        }
    }

    @SuppressWarnings("rawtypes")
    private Map<String, String> schemaDocuments(ObjectMapper mapper, Map<String, Schema> schemaMap,
                                                String schemasUri, Map<String, Set<String>> childrenByParent) {
        if (schemaMap == null || schemaMap.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        schemaMap.forEach((name, schema) -> {
            if (name == null || schema == null) {
                return;
            }
            String simpleName = CatsModelUtils.getSimpleRef(name);
            JsonNode schemaNode = mapper.valueToTree(schema);
            if (schemaNode instanceof ObjectNode objectNode) {
                enrichPolymorphicSchemaNode(objectNode, simpleName, schema, schemaMap, schemasUri, childrenByParent, mapper);
            }
            rewriteSchemaReferences(schemaNode, "#/components/schemas/", schemasUri, childrenByParent);
            try {
                result.putIfAbsent(schemasUri + simpleName, mapper.writeValueAsString(schemaNode));
            } catch (IOException e) {
                throw new IllegalArgumentException("Unable to serialize OpenAPI schema " + name, e);
            }
        });
        return result;
    }

    @SuppressWarnings("rawtypes")
    private static void enrichPolymorphicSchemaNode(ObjectNode objectNode, String simpleName, Schema<?> schema,
                                                    Map<String, Schema> schemaMap, String schemasUri,
                                                    Map<String, Set<String>> childrenByParent, ObjectMapper mapper) {
        populateImplicitDiscriminatorMappings(objectNode, simpleName, schema, schemasUri, childrenByParent);
        constrainChildDiscriminatorValues(objectNode, simpleName, schema, schemaMap, childrenByParent, mapper);
    }

    private static void populateImplicitDiscriminatorMappings(ObjectNode objectNode, String simpleName, Schema<?> schema,
                                                              String schemasUri, Map<String, Set<String>> childrenByParent) {
        Discriminator discriminator = schema.getDiscriminator();
        Set<String> children = childrenByParent.get(simpleName);
        if (discriminator == null || StringUtils.isBlank(discriminator.getPropertyName())
                || children == null || children.isEmpty()) {
            return;
        }
        ObjectNode mappingNode = objectNode.withObject("/discriminator").withObject("/mapping");
        for (String child : children) {
            for (String alias : discriminatorValuesForChild(child, discriminator)) {
                if (!mappingNode.has(alias)) {
                    mappingNode.put(alias, schemasUri + child);
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void constrainChildDiscriminatorValues(ObjectNode objectNode, String simpleName, Schema<?> schema,
                                                          Map<String, Schema> schemaMap,
                                                          Map<String, Set<String>> childrenByParent,
                                                          ObjectMapper mapper) {
        if (!(objectNode.get("allOf") instanceof ArrayNode allOfArray)) {
            return;
        }
        for (String parentSimple : findTransitiveAllOfParents(schema, schemaMap)) {
            Schema<?> parentSchema = schemaMap.get(parentSimple);
            Discriminator parentDisc = parentSchema != null ? parentSchema.getDiscriminator() : null;
            if (parentDisc != null && StringUtils.isNotBlank(parentDisc.getPropertyName())) {
                ObjectNode constraint = mapper.createObjectNode();
                ArrayNode enumNode = constraint.putObject("properties")
                        .putObject(parentDisc.getPropertyName())
                        .putArray("enum");
                Set<String> allowedValues = new LinkedHashSet<>(discriminatorValuesForChild(simpleName, parentDisc));
                for (String descendant : childrenByParent.getOrDefault(simpleName, Set.of())) {
                    allowedValues.addAll(discriminatorValuesForChild(descendant, parentDisc));
                }
                allowedValues.forEach(enumNode::add);
                allOfArray.add(constraint);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static Set<String> findTransitiveAllOfParents(Schema<?> schema, Map<String, Schema> schemaMap) {
        Set<String> visited = new LinkedHashSet<>();
        collectTransitiveParents(schema, schemaMap, visited);
        return visited;
    }

    @SuppressWarnings("rawtypes")
    private static void collectTransitiveParents(Schema<?> schema, Map<String, Schema> schemaMap, Set<String> visited) {
        for (String parent : CatsModelUtils.findAllOfParents(schema)) {
            if (visited.add(parent) && schemaMap != null) {
                collectTransitiveParents(schemaMap.get(parent), schemaMap, visited);
            }
        }
    }

    private static Set<String> discriminatorValuesForChild(String childSimpleName, Discriminator discriminator) {
        Set<String> values = new LinkedHashSet<>();
        if (discriminator.getMapping() != null) {
            discriminator.getMapping().forEach((key, target) -> {
                if (childSimpleName.equalsIgnoreCase(CatsModelUtils.getSimpleRef(target))) {
                    values.add(key);
                }
            });
        }
        if (values.isEmpty()) {
            values.add(childSimpleName);
            values.add(WordUtils.convertToDetectedCasing(childSimpleName, "UPPER_SNAKE_CASE"));
            values.add(WordUtils.convertToDetectedCasing(childSimpleName, "lower_snake_case"));
            values.add(WordUtils.convertToDetectedCasing(childSimpleName, "camelCase"));
            values.add(WordUtils.convertToDetectedCasing(childSimpleName, "kebab-case"));
            values.add(childSimpleName.toLowerCase(Locale.ROOT));
        }
        return values;
    }

    private void rewriteSchemaReferences(JsonNode node, String sourcePrefix, String targetPrefix,
                                         Map<String, Set<String>> childrenByParent) {
        if (node instanceof ObjectNode objectNode) {
            prunePolymorphicBaseRefs(objectNode.get("oneOf"), childrenByParent);
            prunePolymorphicBaseRefs(objectNode.get("anyOf"), childrenByParent);
            JsonNode reference = objectNode.get("$ref");
            if (reference != null && reference.isTextual() && reference.textValue().startsWith(sourcePrefix)) {
                objectNode.put("$ref", targetPrefix + reference.textValue().substring(sourcePrefix.length()));
            }
            rewriteDiscriminatorMapping(objectNode, sourcePrefix, targetPrefix);
            objectNode.elements().forEachRemaining(child -> rewriteSchemaReferences(child, sourcePrefix, targetPrefix, childrenByParent));
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.elements().forEachRemaining(child -> rewriteSchemaReferences(child, sourcePrefix, targetPrefix, childrenByParent));
        }
    }

    private static void prunePolymorphicBaseRefs(JsonNode compositionNode, Map<String, Set<String>> childrenByParent) {
        if (!(compositionNode instanceof ArrayNode arrayNode) || arrayNode.size() <= 1 || childrenByParent.isEmpty()) {
            return;
        }
        Set<String> refNames = new LinkedHashSet<>();
        for (JsonNode element : arrayNode) {
            JsonNode refNode = element != null ? element.get("$ref") : null;
            if (refNode != null && refNode.isTextual()) {
                refNames.add(CatsModelUtils.getSimpleRef(refNode.textValue()));
            }
        }
        Set<String> parentsToPrune = new LinkedHashSet<>();
        for (String refName : refNames) {
            Set<String> children = childrenByParent.get(refName);
            if (children != null && !children.isEmpty() && refNames.containsAll(children)) {
                parentsToPrune.add(refName);
            }
        }
        if (parentsToPrune.isEmpty() || parentsToPrune.size() >= arrayNode.size()) {
            return;
        }
        for (int i = arrayNode.size() - 1; i >= 0; i--) {
            JsonNode refNode = arrayNode.get(i) != null ? arrayNode.get(i).get("$ref") : null;
            if (refNode != null && refNode.isTextual()
                    && parentsToPrune.contains(CatsModelUtils.getSimpleRef(refNode.textValue()))) {
                arrayNode.remove(i);
            }
        }
    }

    private void rewriteDiscriminatorMapping(ObjectNode objectNode, String sourcePrefix, String targetPrefix) {
        JsonNode discriminator = objectNode.get("discriminator");
        if (!(discriminator instanceof ObjectNode discriminatorNode)) {
            return;
        }
        JsonNode defaultMapping = discriminatorNode.get("defaultMapping");
        if (defaultMapping != null && defaultMapping.isTextual()) {
            discriminatorNode.put("defaultMapping", rewriteDiscriminatorTarget(defaultMapping.textValue(), sourcePrefix, targetPrefix));
        }
        JsonNode mapping = discriminatorNode.get("mapping");
        if (!(mapping instanceof ObjectNode mappingNode)) {
            return;
        }
        List<String> keys = new ArrayList<>();
        mappingNode.fieldNames().forEachRemaining(keys::add);
        for (String key : keys) {
            JsonNode target = mappingNode.get(key);
            if (target != null && target.isTextual()) {
                mappingNode.put(key, rewriteDiscriminatorTarget(target.textValue(), sourcePrefix, targetPrefix));
            }
        }
    }

    private String rewriteDiscriminatorTarget(String value, String sourcePrefix, String targetPrefix) {
        if (value.startsWith(sourcePrefix)) {
            return targetPrefix + value.substring(sourcePrefix.length());
        }
        if (!value.contains("/") && !value.contains(":")) {
            return targetPrefix + value;
        }
        return value;
    }

    private String formatError(Error error) {
        String location = String.valueOf(error.getInstanceLocation());
        if (location.isBlank()) {
            location = "$";
        }
        return location + ": " + error.getMessage();
    }

    record ValidationResult(boolean performed, boolean matches, List<String> errors) {
        static ValidationResult notPerformed() {
            return new ValidationResult(false, true, List.of());
        }

        static ValidationResult valid() {
            return new ValidationResult(true, true, List.of());
        }

        static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(true, false, List.copyOf(new LinkedHashSet<>(errors)));
        }

        boolean isValid() {
            return matches;
        }

        String details() {
            if (errors.isEmpty()) {
                return "";
            }
            int displayed = Math.min(5, errors.size());
            List<String> result = new ArrayList<>(errors.subList(0, displayed));
            if (errors.size() > displayed) {
                result.add("... and " + (errors.size() - displayed) + " more validation errors");
            }
            return String.join("; ", result);
        }
    }
}
