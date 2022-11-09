package com.endava.cats.generator.format.api;

import io.swagger.v3.oas.models.media.Schema;

/**
 * Generates valid data based on the OpenAPI format.
 */
public interface ValidDataFormatGenerator extends DataFormatGenerator {

    /**
     * Generates a value based on the given format
     *
     * @param schema the type of the property
     * @return a valid value matching the format
     */
    Object generate(Schema<?> schema);
}
