package com.endava.cats.openapi.handler.api;

/**
 * Triplet of schema location: path, method, and fully qualified name (FQN).
 */
public record SchemaLocation(String path, String method, String fqn) {
}
