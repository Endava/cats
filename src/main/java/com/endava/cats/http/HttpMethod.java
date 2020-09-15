package com.endava.cats.http;

import java.util.Arrays;
import java.util.Optional;

public enum HttpMethod {
    POST, PUT, GET, TRACE, DELETE, PATCH, HEAD;

    public static Optional<HttpMethod> fromString(String httpM) {
        return Arrays.stream(values())
                .filter(enV -> enV.name().equalsIgnoreCase(httpM))
                .findFirst();
    }
}
