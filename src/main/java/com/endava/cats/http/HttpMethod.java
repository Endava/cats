package com.endava.cats.http;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Enumeration with HTTP methods.
 */
public enum HttpMethod {
    //REST
    /**
     * HTTP POST method.
     */
    POST,

    /**
     * HTTP PUT method.
     */
    PUT,

    /**
     * HTTP GET method.
     */
    GET,

    /**
     * HTTP TRACE method.
     */
    TRACE,

    /**
     * HTTP DELETE method.
     */
    DELETE,

    /**
     * HTTP PATCH method.
     */
    PATCH,

    /**
     * HTTP HEAD method.
     */
    HEAD,

    /**
     * HTTP CONNECT method.
     */
    CONNECT,

    // WebDAV
    /**
     * HTTP COPY method.
     */
    COPY,

    /**
     * HTTP MOVE method.
     */
    MOVE,

    /**
     * HTTP PROPPATCH method.
     */
    PROPPATCH,

    /**
     * HTTP PROPFIND method.
     */
    PROPFIND,

    /**
     * HTTP MKCOL method.
     */
    MKCOL,

    /**
     * HTTP LOCK method.
     */
    LOCK,

    /**
     * HTTP UNLOCK method.
     */
    UNLOCK,

    /**
     * HTTP SEARCH method.
     */
    SEARCH,

    /**
     * HTTP BIND method.
     */
    BIND,

    /**
     * HTTP UNBIND method.
     */
    UNBIND,

    /**
     * HTTP REBIND method.
     */
    REBIND,

    /**
     * HTTP MKREDIRECTREF method.
     */
    MKREDIRECTREF,

    /**
     * HTTP UPDATEREDIRECTREF method.
     */
    UPDATEREDIRECTREF,

    /**
     * HTTP ORDERPATCH method.
     */
    ORDERPATCH,

    /**
     * HTTP ACL method.
     */
    ACL,

    /**
     * HTTP REPORT method.
     */
    REPORT,
    // HYPOTHETICAL METHODS
    /**
     * HTTP DIFF method
     */
    DIFF,
    /**
     * HTTP VERIFY method
     */
    VERIFY,
    /**
     * HTTP PUBLISH method
     */
    PUBLISH,
    /**
     * HTTP UNPUBLISH method
     */
    UNPUBLISH,
    /**
     * HTTP BATCH method
     */
    BATCH,
    /**
     * HTTP VIEW method
     */
    VIEW,
    /**
     * HTTP PURGE method
     */
    PURGE,
    /**
     * HTTP DEBUG method
     */
    DEBUG,
    /**
     * HTTP SUBSCRIBE method
     */
    SUBSCRIBE,
    /**
     * HTTP UNSUBSCRIBE method
     */
    UNSUBSCRIBE,
    /**
     * HTTP MERGE method
     */
    MERGE,
    /**
     * HTTP INDEX method
     */
    INDEX;

    private static final List<HttpMethod> REST_METHODS = Arrays.asList(POST, PUT, GET, TRACE, DELETE, PATCH, HEAD);

    private static final List<HttpMethod> NON_REST_METHODS = Arrays.asList(CONNECT, COPY, MOVE,
            PROPPATCH, PROPFIND, MKCOL, LOCK, UNLOCK, SEARCH,
            BIND, UNBIND, REBIND, MKREDIRECTREF,
            UPDATEREDIRECTREF, ORDERPATCH, ACL, REPORT);

    private final static List<HttpMethod> HYPOTHETICAL_HTTP_METHODS = List.of(
            DIFF, VERIFY, PUBLISH, UNPUBLISH, BATCH, VIEW,
            PURGE, DEBUG, SUBSCRIBE, UNSUBSCRIBE, MERGE, INDEX);

    /**
     * Holds a mapping between OpenAPI paths and HTTP methods.
     */
    public static final Map<HttpMethod, Function<PathItem, Operation>> OPERATIONS = new EnumMap<>(HttpMethod.class);
    private static final Map<HttpMethod, List<String>> RECOMMENDED_CODES = new EnumMap<>(HttpMethod.class);
    private static final String TWOXX = "200|201|202|204";

    static {
        OPERATIONS.put(POST, PathItem::getPost);
        OPERATIONS.put(PUT, PathItem::getPut);
        OPERATIONS.put(GET, PathItem::getGet);
        OPERATIONS.put(TRACE, PathItem::getTrace);
        OPERATIONS.put(DELETE, PathItem::getDelete);
        OPERATIONS.put(PATCH, PathItem::getPatch);
        OPERATIONS.put(HEAD, PathItem::getHead);

        RECOMMENDED_CODES.put(HttpMethod.POST, Arrays.asList("400", "500", TWOXX));
        RECOMMENDED_CODES.put(HttpMethod.PUT, Arrays.asList("400", "404", "500", TWOXX));
        RECOMMENDED_CODES.put(HttpMethod.GET, Arrays.asList("404", "500", "200|202"));
        RECOMMENDED_CODES.put(HttpMethod.HEAD, Arrays.asList("404", "200|202"));
        RECOMMENDED_CODES.put(HttpMethod.DELETE, Arrays.asList("404", "500", TWOXX));
        RECOMMENDED_CODES.put(HttpMethod.PATCH, Arrays.asList("400", "404", "500", TWOXX));
        RECOMMENDED_CODES.put(HttpMethod.TRACE, Arrays.asList("500", "200"));
    }

    /**
     * Gets a list of HTTP methods that are considered non-RESTful.
     *
     * @return A list of non-RESTful HTTP methods.
     */
    public static List<HttpMethod> nonRestMethods() {
        return NON_REST_METHODS;
    }

    /**
     * Gets a list of hypothetical HTTP methods.
     *
     * @return a list of non-RESTful hypothetical HTTP methods.
     */
    public static List<HttpMethod> hypotheticalMethods() {
        return HYPOTHETICAL_HTTP_METHODS;
    }

    /**
     * Gets a list of HTTP methods that are considered RESTful.
     *
     * @return A list of RESTful HTTP methods.
     */
    public static List<HttpMethod> restMethods() {
        return REST_METHODS;
    }

    /**
     * Checks if the given HTTP method requires a request body.
     *
     * @param method The HTTP method to check.
     * @return {@code true} if the HTTP method requires a request body, {@code false} otherwise.
     */
    public static boolean requiresBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH || method == HttpMethod.PROPPATCH || method == HttpMethod.REPORT;
    }

    /**
     * Checks if the given HTTP method, represented as a String, requires a request body.
     *
     * @param method The HTTP method as a String to check.
     * @return {@code true} if the HTTP method requires a request body, {@code false} otherwise.
     * @throws IllegalArgumentException If the provided method String is not a valid HTTP method.
     */
    public static boolean requiresBody(String method) {
        return requiresBody(HttpMethod.valueOf(method));
    }

    /**
     * Retrieves the OpenAPI Operation associated with a specific HTTP method and PathItem.
     *
     * @param method   The HTTP method for which to retrieve the Operation.
     * @param pathItem The OpenAPI PathItem associated with the endpoint.
     * @return The OpenAPI Operation for the specified HTTP method and PathItem.
     * @throws NullPointerException     If the provided HTTP method or PathItem is null.
     * @throws IllegalArgumentException If the provided HTTP method is not supported or the PathItem does not contain the specified method.
     */
    public static Operation getOperation(HttpMethod method, PathItem pathItem) {
        return OPERATIONS.get(method).apply(pathItem);
    }

    /**
     * Converts a case-insensitive HTTP method string to the corresponding HttpMethod enum.
     *
     * @param httpM The HTTP method string to convert.
     * @return An Optional containing the HttpMethod enum if found, or empty if not found.
     * @throws NullPointerException If the provided HTTP method string is null.
     */
    public static Optional<HttpMethod> fromString(String httpM) {
        return Arrays.stream(values())
                .filter(enV -> enV.name().equalsIgnoreCase(httpM))
                .findFirst();
    }

    /**
     * Gets a list of recommended response codes associated with the current ResponseCodeFamily.
     *
     * @return A list of recommended response codes.
     */
    public List<String> getRecommendedCodes() {
        return RECOMMENDED_CODES.get(this);
    }
}
