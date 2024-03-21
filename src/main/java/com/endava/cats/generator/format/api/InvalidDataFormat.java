package com.endava.cats.generator.format.api;

import io.swagger.v3.oas.models.media.Schema;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Keeps a mapping between OpenAPI format types and CATs generators
 */
@Singleton
public class InvalidDataFormat extends DataFormat<InvalidDataFormatGenerator> {
    /**
     * Creates a new InvalidDataFormat.
     *
     * @param generators a list of registered invalid data generators
     */
    @Inject
    public InvalidDataFormat(Instance<InvalidDataFormatGenerator> generators) {
        super(generators);
    }

    /**
     * Returns the proper generator for the provided schema and/or property name.
     *
     * @param schema       the field schema
     * @param propertyName the name of the property
     * @return a generator for the given schema and property name
     */
    public InvalidDataFormatGenerator generator(Schema<?> schema, String propertyName) {
        return super.getGenerators(schema, propertyName)
                .stream()
                .findFirst()
                .orElse(new VoidGenerator());
    }
}
