package com.endava.cats.model;

import com.endava.cats.http.HttpMethod;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Identifies the part of a request changed by a fuzzer. This metadata is used for reporting and
 * to keep reference data or runtime correlation from overwriting deliberate mutations.
 */
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public final class MutationTarget {
    @NonNull
    private final Location location;
    @NonNull
    private final String name;

    /**
     * Supported request mutation locations.
     */
    @RequiredArgsConstructor
    public enum Location {
        BODY("Body field"),
        HEADER("Header"),
        PATH("Path parameter"),
        QUERY("Query parameter"),
        REQUEST_BODY("Request body"),
        HTTP_METHOD("HTTP method");

        @Getter
        private final String displayName;
    }

    public static MutationTarget body(String name) {
        return new MutationTarget(Location.BODY, name);
    }

    public static MutationTarget header(String name) {
        return new MutationTarget(Location.HEADER, name);
    }

    public static MutationTarget path(String name) {
        return new MutationTarget(Location.PATH, name);
    }

    public static MutationTarget query(String name) {
        return new MutationTarget(Location.QUERY, name);
    }

    public static MutationTarget requestBody() {
        return new MutationTarget(Location.REQUEST_BODY, "Entire body");
    }

    public static MutationTarget httpMethod() {
        return new MutationTarget(Location.HTTP_METHOD, "Request method");
    }

    /**
     * Classifies a named request field using the operation metadata available in {@link FuzzingData}.
     *
     * @param data request generation data
     * @param name field or parameter name
     * @return a typed mutation target
     */
    public static MutationTarget requestField(FuzzingData data, String name) {
        return requestFields(data, name).getFirst();
    }

    /**
     * Classifies every request location changed when a named target is mutated. Template and custom
     * fuzzers can change more than one location when the same name is reused in a request.
     *
     * @param data request generation data
     * @param name field or parameter name
     * @return all matching mutation targets, or a body target when no metadata matches
     */
    public static List<MutationTarget> requestFields(FuzzingData data, String name) {
        Set<MutationTarget> targets = new LinkedHashSet<>();
        String rootName = name.substring(0, name.indexOf('#') < 0 ? name.length() : name.indexOf('#'));
        Set<String> queryParams = Optional.ofNullable(data.getQueryParams()).orElseGet(Set::of);
        if ((queryParams.contains(name) || queryParams.contains(rootName)) ||
                isQueryParameter(data.getPath(), name)) {
            targets.add(query(name));
        }
        if (isPathParameter(data.getPath(), name)) {
            targets.add(path(name));
        }
        if (Optional.ofNullable(data.getHeaders()).orElseGet(Set::of).stream()
                .anyMatch(header -> header.getName().equalsIgnoreCase(name))) {
            targets.add(header(name));
        }
        boolean requestHasBody = HttpMethod.requiresBody(data.getMethod());
        if (requestHasBody && hasBodyField(data.getPayload(), name)) {
            targets.add(body(name));
        }
        if (targets.isEmpty()) {
            targets.add(requestHasBody ? body(name) : query(name));
        }
        return List.copyOf(targets);
    }

    public String getLocationName() {
        return location.getDisplayName();
    }

    private static boolean isPathParameter(String requestPath, String name) {
        if (requestPath == null) {
            return false;
        }
        String simpleName = name.substring(name.lastIndexOf('#') + 1);
        return requestPath.contains("{" + simpleName + "}");
    }

    private static boolean isQueryParameter(String requestPath, String name) {
        if (requestPath == null) {
            return false;
        }
        try {
            String query = URI.create(requestPath).getQuery();
            return query != null && Arrays.stream(query.split("&"))
                    .map(pair -> pair.split("=", 2)[0])
                    .anyMatch(parameter -> parameter.equalsIgnoreCase(name));
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    private static boolean hasBodyField(String payload, String name) {
        if (payload == null) {
            return false;
        }
        String simpleName = name.substring(name.lastIndexOf('#') + 1);
        try {
            return containsField(JsonParser.parseString(payload), simpleName);
        } catch (RuntimeException _) {
            return false;
        }
    }

    private static boolean containsField(JsonElement element, String field) {
        if (element.isJsonObject()) {
            if (element.getAsJsonObject().has(field)) {
                return true;
            }
            return element.getAsJsonObject().entrySet().stream()
                    .anyMatch(entry -> containsField(entry.getValue(), field));
        }
        return element.isJsonArray() && element.getAsJsonArray().asList().stream()
                .anyMatch(item -> containsField(item, field));
    }
}
