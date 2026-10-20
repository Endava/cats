package com.endava.cats.report;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
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
import io.swagger.v3.oas.models.media.Schema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ResponseSchemaValidator {
    private static final String SCHEMAS_URI = "https://cats.invalid/schemas/";
    private static final String COMPONENTS_REF_PREFIX = "#/components/schemas/";

    private final IdentityHashMap<OpenAPI, DocumentSchemas> documentCaches = new IdentityHashMap<>();

    private record DocumentSchemas(ObjectMapper mapper, SchemaRegistry registry,
                                   IdentityHashMap<Schema<?>, com.networknt.schema.Schema> compiledByIdentity,
                                   Map<String, com.networknt.schema.Schema> compiledByKey) {
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
            SchemaRegistry registry = SchemaRegistry.withDialect(openApi31
                            ? Dialects.getOpenApi31() : Dialects.getOpenApi30(),
                    builder -> builder.schemas(schemaDocuments(mapper, data.getSchemaMap(), SCHEMAS_URI))
                            .schemaLoader(loader -> loader.fetchRemoteResources(false)));
            return new DocumentSchemas(mapper, registry, new IdentityHashMap<>(), new LinkedHashMap<>());
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
        rewriteSchemaReferences(root, COMPONENTS_REF_PREFIX, SCHEMAS_URI);
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

    private Map<String, String> schemaDocuments(ObjectMapper mapper, Map<String, Schema> schemaMap, String schemasUri) {
        if (schemaMap == null || schemaMap.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        schemaMap.forEach((name, schema) -> {
            if (name == null || schema == null) {
                return;
            }
            JsonNode schemaNode = mapper.valueToTree(schema);
            rewriteSchemaReferences(schemaNode, "#/components/schemas/", schemasUri);
            try {
                result.putIfAbsent(schemasUri + CatsModelUtils.getSimpleRef(name), mapper.writeValueAsString(schemaNode));
            } catch (IOException e) {
                throw new IllegalArgumentException("Unable to serialize OpenAPI schema " + name, e);
            }
        });
        return result;
    }

    private void rewriteSchemaReferences(JsonNode node, String sourcePrefix, String targetPrefix) {
        if (node instanceof ObjectNode objectNode) {
            JsonNode reference = objectNode.get("$ref");
            if (reference != null && reference.isTextual() && reference.textValue().startsWith(sourcePrefix)) {
                objectNode.put("$ref", targetPrefix + reference.textValue().substring(sourcePrefix.length()));
            }
            objectNode.elements().forEachRemaining(child -> rewriteSchemaReferences(child, sourcePrefix, targetPrefix));
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.elements().forEachRemaining(child -> rewriteSchemaReferences(child, sourcePrefix, targetPrefix));
        }
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
