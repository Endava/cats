package com.endava.cats.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;

/**
 * Identifies a concrete part of an HTTP request independently of why that part is being used.
 */
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public final class RequestTarget {
    private static final Comparator<RequestTarget> ORDERING = Comparator
            .comparing(RequestTarget::getLocation)
            .thenComparing(RequestTarget::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(RequestTarget::getName);

    @NonNull
    private final Location location;
    @NonNull
    private final String name;

    /** Supported request locations. */
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

    public static RequestTarget body(String name) {
        return new RequestTarget(Location.BODY, name);
    }

    public static RequestTarget header(String name) {
        return new RequestTarget(Location.HEADER, name);
    }

    public static RequestTarget path(String name) {
        return new RequestTarget(Location.PATH, name);
    }

    public static RequestTarget query(String name) {
        return new RequestTarget(Location.QUERY, name);
    }

    public static RequestTarget requestBody() {
        return new RequestTarget(Location.REQUEST_BODY, "Entire body");
    }

    public static RequestTarget httpMethod() {
        return new RequestTarget(Location.HTTP_METHOD, "Request method");
    }

    /**
     * Returns the canonical ordering used when presenting request targets.
     *
     * @return comparator ordered by location and then field name
     */
    public static Comparator<RequestTarget> ordering() {
        return ORDERING;
    }

    public String getLocationName() {
        return location.getDisplayName();
    }
}
