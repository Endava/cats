package com.endava.cats.model.generator.api;

import io.swagger.v3.oas.models.media.Schema;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ValidDataFormat {

    Instance<ValidDataFormatGenerator> generators;

    @Inject
    public ValidDataFormat(Instance<ValidDataFormatGenerator> generators) {
        this.generators = generators;
    }

    public Object generate(Schema<?> schema, String propertyName) {
        if (schema.getFormat() == null) {
            return null;
        }
        return generators.stream()
                .filter(generator -> generator.appliesTo(schema.getFormat(), propertyName))
                .findFirst()
                .orElse(new ValidDataFormatGenerator.VoidGenerator())
                .generate(schema);
    }
}
