package com.endava.cats.util;

import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CyclicReferenceDetector {
    private final Map<String, Set<String>> schemaGraph = new HashMap<>();
    private final Map<String, Schema> schemasById = new HashMap<>();
    private final int maxDepth;

    public CyclicReferenceDetector(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /**
     * Records a schema and its relationships for cycle detection
     *
     * @param schemaId Unique identifier for the schema
     * @param schema   The schema to analyze
     */
    public void recordSchema(String schemaId, Schema schema) {
        if (schema == null || schemaId == null) {
            return;
        }

        // Store schema for later reference
        schemasById.put(schemaId, schema);
        Set<String> relationships = new HashSet<>();
        schemaGraph.put(schemaId, relationships);

        // Record direct reference relationships
        if (schema.get$ref() != null) {
            relationships.add(CatsModelUtils.getSimpleRef(schema.get$ref()));
        }

        // Record property relationships
        if (schema.getProperties() != null) {
            Set<Map.Entry<String, Schema>> set = schema.getProperties().entrySet();
            for (Map.Entry<String, Schema> property : set) {
                String propertySchemaId = generatePropertySchemaId(schemaId, property.getKey(), property.getValue());
                relationships.add(propertySchemaId);
                recordSchema(propertySchemaId, property.getValue());
            }
        }

        // Record array item relationships
        if (CatsModelUtils.isArraySchema(schema)) {
            Schema itemSchema = CatsModelUtils.getSchemaItems(schema);
            String itemSchemaId = generatePropertySchemaId(schemaId, "items", itemSchema);
            relationships.add(itemSchemaId);
            recordSchema(itemSchemaId, itemSchema);
        }

        // Record additional properties relationships
        if (schema.getAdditionalProperties() instanceof Schema additionalPropsSchema) {
            String additionalPropsId = generatePropertySchemaId(schemaId, "additionalProperties", additionalPropsSchema);
            relationships.add(additionalPropsId);
            recordSchema(additionalPropsId, additionalPropsSchema);
        }

        // Record composed schema relationships
        recordComposedSchemaRelationships(schemaId, schema, relationships);
    }

    /**
     * Checks if the current path would create a cycle exceeding maxDepth
     *
     * @param currentPath The current property path being processed
     * @param schema      The current schema being processed
     * @return true if a harmful cycle is detected
     */
    public boolean wouldCreateCycle(String currentPath, Schema schema) {
        if (currentPath == null || schema == null) {
            return false;
        }

        // Generate unique ID for current schema
        String currentSchemaId = getSchemaIdForPath(currentPath, schema);

        // Check for cycles in the path
        Set<String> visited = new HashSet<>();
        Deque<PathNode> stack = new ArrayDeque<>();
        stack.push(new PathNode(currentSchemaId, 0));

        while (!stack.isEmpty()) {
            PathNode current = stack.pop();

            // Check if we've exceeded max depth
            if (current.depth > maxDepth) {
                return true;
            }

            // Skip if we've seen this node before
            if (!visited.add(current.schemaId)) {
                continue;
            }

            // Get relationships for current schema
            Set<String> relationships = schemaGraph.get(current.schemaId);
            if (relationships == null) {
                continue;
            }

            // Check all related schemas
            for (String relatedSchemaId : relationships) {
                // If we find the starting schema ID again, we have a cycle
                if (relatedSchemaId.equals(currentSchemaId)) {
                    return true;
                }

                stack.push(new PathNode(relatedSchemaId, current.depth + 1));
            }
        }

        return false;
    }

    private void recordComposedSchemaRelationships(String schemaId, Schema schema, Set<String> relationships) {
        // Handle allOf
        if (schema.getAllOf() != null) {
            List<Schema> allOf = schema.getAllOf();
            for (Schema allOfSchema : allOf) {
                String allOfId = generatePropertySchemaId(schemaId, "allOf", allOfSchema);
                relationships.add(allOfId);
                recordSchema(allOfId, allOfSchema);
            }
        }

        // Handle oneOf
        if (schema.getOneOf() != null) {
            List<Schema> oneOf = schema.getOneOf();
            for (Schema oneOfSchema : oneOf) {
                String oneOfId = generatePropertySchemaId(schemaId, "oneOf", oneOfSchema);
                relationships.add(oneOfId);
                recordSchema(oneOfId, oneOfSchema);
            }
        }

        // Handle anyOf
        if (schema.getAnyOf() != null) {
            List<Schema> anyOf = schema.getAnyOf();
            for (Schema anyOfSchema : anyOf) {
                String anyOfId = generatePropertySchemaId(schemaId, "anyOf", anyOfSchema);
                relationships.add(anyOfId);
                recordSchema(anyOfId, anyOfSchema);
            }
        }
    }

    private String generatePropertySchemaId(String parentId, String propertyName, Schema schema) {
        if (schema.get$ref() != null) {
            return CatsModelUtils.getSimpleRef(schema.get$ref());
        }

        // Generate a unique ID based on schema properties
        StringBuilder idBuilder = new StringBuilder(parentId)
                .append("#")
                .append(propertyName);

        // Add type information
        if (schema.getType() != null) {
            idBuilder.append("_").append(schema.getType());
        }

        // Add format information if present
        if (schema.getFormat() != null) {
            idBuilder.append("_").append(schema.getFormat());
        }

        return idBuilder.toString();
    }

    private String getSchemaIdForPath(String path, Schema schema) {
        if (schema.get$ref() != null) {
            return CatsModelUtils.getSimpleRef(schema.get$ref());
        }
        return path;
    }

    private static class PathNode {
        final String schemaId;
        final int depth;

        PathNode(String schemaId, int depth) {
            this.schemaId = schemaId;
            this.depth = depth;
        }
    }
}
