package com.endava.cats.openapi.handler.api;

import io.swagger.v3.oas.models.media.Schema;

/**
 * A handler that processes OpenAPI schema nodes during a deep traversal of the OpenAPI document.
 * Implementations can define custom logic to handle each schema encountered.
 */
@FunctionalInterface
public interface SchemaHandler {
    /**
     * @param schemaLocation the location of the schema in the OpenAPI document, including path, method, and fully qualified name (FQN)
     * @param schema         the actual Schema node
     */
    void handle(SchemaLocation schemaLocation, Schema<?> schema);
}
