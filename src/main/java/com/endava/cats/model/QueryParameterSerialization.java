package com.endava.cats.model;

/**
 * OpenAPI serialization settings for a query parameter.
 *
 * @param style   the OpenAPI parameter style
 * @param explode whether values are expanded into separate query parameters according to the selected style
 */
public record QueryParameterSerialization(String style, boolean explode) {
    public static final String FORM = "form";
    public static final String DEEP_OBJECT = "deepObject";

    /**
     * The OpenAPI defaults for query parameters are {@code style: form} and {@code explode: true}.
     */
    public static QueryParameterSerialization defaults() {
        return new QueryParameterSerialization(FORM, true);
    }
}
