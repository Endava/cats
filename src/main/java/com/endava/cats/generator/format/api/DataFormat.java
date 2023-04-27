package com.endava.cats.generator.format.api;

import io.swagger.v3.oas.models.media.Schema;

import jakarta.enterprise.inject.Instance;
import java.util.Optional;

public abstract class DataFormat<T extends DataFormatGenerator> {

    final Instance<T> generators;

    protected DataFormat(Instance<T> generators) {
        this.generators = generators;
    }

    Optional<T> getGenerator(Schema<?> schema, String propertyName) {
        return generators.stream()
                .filter(generator -> generator.appliesTo(Optional.ofNullable(schema.getFormat()).orElse(""),
                        Optional.ofNullable(propertyName).orElse("")))
                .findFirst();
    }
}
