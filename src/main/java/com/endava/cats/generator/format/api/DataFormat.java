package com.endava.cats.generator.format.api;

import io.swagger.v3.oas.models.media.Schema;

import javax.enterprise.inject.Instance;
import java.util.Optional;

public abstract class DataFormat<T extends DataFormatGenerator> {

    Instance<T> generators;

    protected DataFormat(Instance<T> generators) {
        this.generators = generators;
    }

    Optional<T> getGenerator(Schema<?> schema, String propertyName) {
        if (schema.getFormat() == null) {
            return Optional.empty();
        }
        return generators.stream()
                .filter(generator -> generator.appliesTo(schema.getFormat(), propertyName))
                .findFirst();
    }
}
