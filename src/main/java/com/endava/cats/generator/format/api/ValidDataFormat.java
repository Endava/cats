package com.endava.cats.generator.format.api;

import io.swagger.v3.oas.models.media.Schema;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * A subclass of {@link DataFormat} that represents a valid data format.
 */
@Singleton
public class ValidDataFormat extends DataFormat<ValidDataFormatGenerator> {

    /**
     * Injects an {@link Instance} of {@link ValidDataFormatGenerator} into the constructor.
     *
     * @param generators An {@link Instance} of {@link ValidDataFormatGenerator}
     */
    @Inject
    public ValidDataFormat(Instance<ValidDataFormatGenerator> generators) {
        super(generators);
    }

    /**
     * Generates data for the given schema and property name. If no suitable generator is found, it uses a {@link VoidGenerator}.
     *
     * @param schema       The schema for the property
     * @param propertyName The name of the property
     * @return The generated data
     */
    public Object generate(Schema<?> schema, String propertyName) {
        return super.getGenerators(schema, propertyName)
                .stream()
                .findFirst()
                .orElse(new VoidGenerator())
                .generate(schema);
    }
}
