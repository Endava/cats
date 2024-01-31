package com.endava.cats.generator.format.api;

import io.swagger.v3.oas.models.media.Schema;
import jakarta.enterprise.inject.Instance;

import java.util.Optional;

/**
 * An abstract class that represents a data format.
 *
 * @param <T> The type of {@code DataFormatGenerator} associated with this data format.
 */
public abstract class DataFormat<T extends DataFormatGenerator> {

    final Instance<T> generators;

    /**
     * Constructs a new data format with the specified set of data format generators.
     *
     * @param generators The set of data format generators for this data format
     */
    protected DataFormat(Instance<T> generators) {
        this.generators = generators;
    }

    /**
     * Finds the data format generator that is appropriate for the given schema and property name.
     *
     * @param schema       The schema for the property
     * @param propertyName The name of the property
     * @return An optional containing the data format generator for the property, if it exists
     */
    Optional<T> getGenerator(Schema<?> schema, String propertyName) {
        return generators.stream()
                .filter(generator -> generator.appliesTo(Optional.ofNullable(schema.getFormat()).orElse(""),
                        Optional.ofNullable(propertyName).orElse("")))
                .findFirst();
    }
}
