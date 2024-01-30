package com.endava.cats.factory;

import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Represents a custom media type indicating an empty body.
 * Extends {@link io.swagger.v3.oas.models.media.MediaType MediaType}.
 */
public class NoMediaType extends MediaType {
    /**
     * Constant representing an empty body.
     */
    public static final String EMPTY_BODY = "empty_body";

    /**
     * Schema for an empty body.
     */
    public static final Schema<?> EMPTY_BODY_SCHEMA;

    static {
        EMPTY_BODY_SCHEMA = new Schema<>();
        EMPTY_BODY_SCHEMA.set$ref(EMPTY_BODY);
        EMPTY_BODY_SCHEMA.setExample("{}");
    }

    @Override
    public Schema<?> getSchema() {
        return EMPTY_BODY_SCHEMA;
    }
}
