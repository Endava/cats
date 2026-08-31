package com.endava.cats.model;

/**
 * OpenAPI serialization settings for a query parameter.
 *
 * @param style   the OpenAPI parameter style
 * @param explode whether array values are expanded into repeated query parameters
 */
public record QueryParameterSerialization(String style, boolean explode) {
    public static final String FORM = "form";

    /**
     * The OpenAPI defaults for query parameters are {@code style: form} and {@code explode: true}.
     */
    public static QueryParameterSerialization defaults() {
        return new QueryParameterSerialization(FORM, true);
    }
}
