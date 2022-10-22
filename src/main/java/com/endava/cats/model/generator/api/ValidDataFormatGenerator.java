package com.endava.cats.model.generator.api;

import io.swagger.v3.oas.models.media.Schema;

/**
 * Generates valid data based on the OpenAPI format.
 */
public interface ValidDataFormatGenerator {

    /**
     * Generates a value based on the given format
     *
     * @param schema the type of the property
     * @return a valid value matching the format
     */
    Object generate(Schema<?> schema);

    /**
     * Returns a Predicate to check if the Generator applies to the given format or propertyName
     *
     * @param format       the OpenAPI format
     * @param propertyName the name of tje property
     * @return true if the Generator can be applied to the given input
     */
    boolean appliesTo(String format, String propertyName);

    class VoidGenerator implements ValidDataFormatGenerator {

        @Override
        public Object generate(Schema<?> format) {
            return null;
        }

        @Override
        public boolean appliesTo(String format, String propertyName) {
            return true;
        }
    }
}
