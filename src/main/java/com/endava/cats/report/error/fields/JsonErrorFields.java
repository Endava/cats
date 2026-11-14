package com.endava.cats.report.error.fields;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Optional;

/**
 * The well-known fields of JSON error bodies and how to read the error type and detail from them.
 */
public final class JsonErrorFields {
    static final List<String> ERROR_TYPE_KEYS = List.of("exception", "errorCode", "code", "title", "error", "type");
    public static final List<String> STACK_KEYS = List.of("trace", "stackTrace", "stack", "exceptionTrace");
    private static final List<String> NESTED_KEYS = List.of("code", "type", "message", "detail");
    private static final List<String> TITLE_KEYS = List.of("title", "error", "code", "errorCode");

    private JsonErrorFields() {
    }

    /**
     * Reads the error type. A problem-details {@code type} URI or {@code about:blank} is replaced by the title, which
     * stays the same across occurrences of the problem.
     *
     * @param obj the error object
     * @return the error type, or empty
     */
    public static String errorType(JsonObject obj) {
        String errorType = firstStringProperty(obj, ERROR_TYPE_KEYS);
        if (!isProblemTypeUri(errorType)) {
            return errorType;
        }
        String title = firstStringProperty(obj, TITLE_KEYS);
        return title.isEmpty() ? errorType : title;
    }

    private static boolean isProblemTypeUri(String errorType) {
        return errorType.startsWith("http://") || errorType.startsWith("https://") || "about:blank".equalsIgnoreCase(errorType);
    }

    /**
     * Returns the first non-blank primitive among the given keys; an object value contributes its own
     * {@code code}, {@code type}, {@code message} or {@code detail}.
     *
     * @param obj           the JSON object
     * @param candidateKeys the keys, in order of preference
     * @return the trimmed value, or empty
     */
    public static String firstStringProperty(JsonObject obj, List<String> candidateKeys) {
        return candidateKeys.stream().map(obj::get).map(JsonErrorFields::stringValue).flatMap(Optional::stream)
                .findFirst().orElse("");
    }

    private static Optional<String> stringValue(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            return Optional.of(element.getAsString().trim()).filter(value -> !value.isEmpty());
        }
        if (element != null && element.isJsonObject()) {
            return Optional.of(firstStringProperty(element.getAsJsonObject(), NESTED_KEYS)).filter(value -> !value.isEmpty());
        }
        return Optional.empty();
    }
}
