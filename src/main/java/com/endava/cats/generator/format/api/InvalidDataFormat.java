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

    public InvalidDataFormatGenerator generator(Schema<?> schema, String propertyName) {
        return super.getGenerator(schema, propertyName).orElse(new VoidGenerator());
    }
}
