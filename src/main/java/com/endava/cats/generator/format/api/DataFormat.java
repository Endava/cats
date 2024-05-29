package com.endava.cats.generator.format.api;

import io.swagger.v3.oas.models.media.Schema;
import jakarta.enterprise.inject.Instance;

import java.util.List;
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
    List<T> getGenerators(Schema<?> schema, String propertyName) {
        return generators.stream()
                .filter(generator -> generator.appliesTo(Optional.ofNullable(schema.getFormat()).orElse(""),
                        Optional.ofNullable(propertyName).orElse("")))
                .toList();
    }

    public static Object matchesPatternOrNull(Schema<?> schema, Object generated) {
        if ((schema.getPattern() == null || (schema.getPattern() != null && String.valueOf(generated).matches(schema.getPattern())))
                && (schema.getMaxLength() == null || (String.valueOf(generated).length() <= schema.getMaxLength()))) {
            return generated;
        }

        return null;
    }

    public static Object matchesPatternOrNullWithCombinations(Schema<?> schema, String generated) {
        Object attempt = null;

        for (String variant : List.of(generated, generated.replace(" ", ""), generated.replace(" ", "-"))) {
            attempt = DataFormat.matchesPatternOrNull(schema, variant);
            if (attempt != null) {
                break;
            }
        }

        return attempt;
    }
}
