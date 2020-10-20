package com.endava.cats.http;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Optional;
import java.util.function.Function;

public enum HttpMethod {
    POST, PUT, GET, TRACE, DELETE, PATCH, HEAD;

    private static final EnumMap<HttpMethod, Function<PathItem, Operation>> OPERATIONS = new EnumMap<>(HttpMethod.class);

    static {
        OPERATIONS.put(POST, PathItem::getPost);
        OPERATIONS.put(PUT, PathItem::getPut);
        OPERATIONS.put(GET, PathItem::getGet);
        OPERATIONS.put(TRACE, PathItem::getTrace);
        OPERATIONS.put(DELETE, PathItem::getDelete);
        OPERATIONS.put(PATCH, PathItem::getPatch);
        OPERATIONS.put(HEAD, PathItem::getHead);
    }

    public static Operation getOperation(HttpMethod method, PathItem pathItem) {
        return OPERATIONS.get(method).apply(pathItem);
    }

    public static Optional<HttpMethod> fromString(String httpM) {
        return Arrays.stream(values())
                .filter(enV -> enV.name().equalsIgnoreCase(httpM))
                .findFirst();
    }
}
