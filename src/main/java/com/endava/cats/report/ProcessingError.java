package com.endava.cats.report;

/**
 * Represents an error that occurred during the fuzzer processing.
 */
public record ProcessingError(String path, String httpMethod, String message) {

    @Override
    public String toString() {
        String firstPart = path != null ? "Path %s".formatted(path) : "";
        String secondPart = httpMethod != null ? ", http method %s: ".formatted(httpMethod) : "";

        return "%s%s%s".formatted(firstPart, secondPart, message);
    }
}


