package com.endava.cats.openapi.handler.api;

import com.endava.cats.http.HttpMethod;

/**
 * Triplet of schema location: path, method, and fully qualified name (FQN).
 */
public record SchemaLocation(String path, String method, String fqn, String pointer) {

    /**
     * Checks if the given path and method match this schema location.
     *
     * @param path   the path to check
     * @param method the HTTP method to check
     * @return true if the path and method match this schema location, false otherwise
     */
    public boolean matchesPathAndMethod(String path, HttpMethod method) {
        if (this.path == null || this.method == null || path == null || method == null) {
            return false;
        }
        return this.path.equalsIgnoreCase(path) && this.method.equalsIgnoreCase(method.name());
    }


    /**
     * Checks if this schema location is global, meaning it has no specific path or method associated with it.
     *
     * @return true if this schema location is global, false otherwise
     */
    public boolean isGlobalLocation() {
        return path == null && method == null;
    }
}
