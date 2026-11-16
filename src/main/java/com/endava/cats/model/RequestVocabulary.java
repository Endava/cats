package com.endava.cats.model;

import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The field names and free-text values sent in a request. Error template mining uses it to mask request data
 * echoed back in error responses, which generic log parsers can only guess at.
 *
 * @param fieldNames JSON keys (at any depth), query parameter and form field names
 * @param values     primitive JSON, query parameter, form field and path parameter values that are long enough to
 *                   identify reflected input
 */
public record RequestVocabulary(Set<String> fieldNames, Set<String> values) {
    public static final RequestVocabulary EMPTY = new RequestVocabulary(Set.of(), Set.of());
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_DEPTH = 16;
    private static final int MIN_VALUE_LENGTH = 3;
    private static final int MAX_VALUE_LENGTH = 200;
    private static final Set<String> NON_IDENTIFYING_VALUES = Set.of("true", "false", "null");
    private static final Pattern FORM_BODY = Pattern.compile("[\\w.\\[\\]%-]+=[^&\\s]*(?:&[\\w.\\[\\]%-]+=[^&\\s]*)*+");

    /**
     * Builds the vocabulary of a request from its JSON or form payload, its query string and its path parameter values.
     *
     * @param payload      the request payload; payloads that are neither JSON nor form-urlencoded contribute nothing
     * @param url          the request URL
     * @param contractPath the contract path (e.g. {@code /pets/{id}}) used to locate path parameter values in the URL,
     *                     or {@code null} to ignore path parameters
     * @return the request vocabulary
     */
    public static RequestVocabulary from(String payload, String url, String contractPath) {
        return fromPayload(payload, url, contractPath, parseJson(payload));
    }

    /**
     * Builds the historical vocabulary used by WFC fault contexts. Its structured-JSON guard intentionally excludes
     * simple root primitives, preserving existing WFC fault identities while problem identities capture all JSON values.
     *
     * @param payload      the request payload
     * @param url          the request URL
     * @param contractPath the contract path used to locate path parameters
     * @return the vocabulary with the historical body guard
     */
    public static RequestVocabulary fromStructuredPayload(String payload, String url, String contractPath) {
        return fromPayload(payload, url, contractPath, JsonUtils.isValidJson(payload) ? parseJson(payload) : null);
    }

    private static RequestVocabulary fromPayload(String payload, String url, String contractPath, JsonElement root) {
        String form = formPayload(payload, root);
        Set<String> names = Stream.of(names(root, 0), queryParts(url, 0), formParts(form, 0)).flatMap(Function.identity())
                .filter(StringUtils::isNotBlank).limit(MAX_ENTRIES).collect(Collectors.toUnmodifiableSet());
        Set<String> values = Stream.of(values(root, 0), queryParts(url, 1), formParts(form, 1), pathParameterValues(url, contractPath))
                .flatMap(Function.identity())
                .filter(RequestVocabulary::isIdentifyingValue)
                .limit(MAX_ENTRIES).collect(Collectors.toUnmodifiableSet());
        return new RequestVocabulary(names, values);
    }

    /**
     * Aligns the contract path with the end of the URL path (the URL may carry a base path) and returns the URL
     * segments sitting at {@code {parameter}} positions.
     */
    private static Stream<String> pathParameterValues(String url, String contractPath) {
        String[] templateSegments = StringUtils.split(StringUtils.defaultString(contractPath), '/');
        String[] urlSegments = StringUtils.split(urlPath(url), '/');
        int offset = urlSegments.length - templateSegments.length;
        if (offset < 0 || !StringUtils.contains(contractPath, '{')) {
            return Stream.empty();
        }
        return IntStream.range(0, templateSegments.length)
                .filter(index -> templateSegments[index].startsWith("{"))
                .mapToObj(index -> decodeQuietly(urlSegments[offset + index]));
    }

    private static String urlPath(String url) {
        String withoutQuery = StringUtils.substringBefore(StringUtils.defaultString(url), "?");
        return withoutQuery.contains("://")
                ? StringUtils.substringAfter(StringUtils.substringAfter(withoutQuery, "://"), "/")
                : withoutQuery;
    }

    private static JsonElement parseJson(String payload) {
        try {
            return JsonUtils.JSON_STRICT_PARSER.fromJson(payload, JsonElement.class);
        } catch (JsonParseException _) {
            return null;
        }
    }

    private static Stream<String> names(JsonElement element, int depth) {
        if (element == null || depth > MAX_DEPTH) {
            return Stream.empty();
        }
        if (element.isJsonObject()) {
            return element.getAsJsonObject().asMap().entrySet().stream()
                    .flatMap(entry -> Stream.concat(Stream.of(entry.getKey()), names(entry.getValue(), depth + 1)));
        }
        return children(element).flatMap(child -> names(child, depth + 1));
    }

    private static Stream<String> values(JsonElement element, int depth) {
        if (element == null || depth > MAX_DEPTH) {
            return Stream.empty();
        }
        if (element.isJsonPrimitive()) {
            return Stream.of(element.getAsString());
        }
        return children(element).flatMap(child -> values(child, depth + 1));
    }

    private static Stream<JsonElement> children(JsonElement element) {
        if (element.isJsonObject()) {
            return element.getAsJsonObject().asMap().values().stream();
        }
        return element.isJsonArray() ? element.getAsJsonArray().asList().stream() : Stream.empty();
    }

    /**
     * Returns the payload when it is an {@code application/x-www-form-urlencoded} body ({@code a=1&b=x%20y}), whose
     * fields servers echo back just like query parameters.
     */
    private static String formPayload(String payload, JsonElement root) {
        return root == null && payload != null && FORM_BODY.matcher(payload.strip()).matches() ? payload.strip() : "";
    }

    private static Stream<String> queryParts(String url, int partIndex) {
        return formParts(StringUtils.substringAfter(StringUtils.defaultString(url), "?"), partIndex);
    }

    private static Stream<String> formParts(String query, int partIndex) {
        return Arrays.stream(StringUtils.split(query, '&'))
                .map(pair -> StringUtils.split(pair, "=", 2))
                .filter(parts -> parts.length > partIndex)
                .map(parts -> parts[partIndex])
                .map(RequestVocabulary::decodeQuietly);
    }

    private static String decodeQuietly(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException _) {
            return value;
        }
    }

    private static boolean isIdentifyingValue(String value) {
        int length = StringUtils.strip(value).length();
        return length >= MIN_VALUE_LENGTH && length <= MAX_VALUE_LENGTH && !NON_IDENTIFYING_VALUES.contains(value);
    }
}
