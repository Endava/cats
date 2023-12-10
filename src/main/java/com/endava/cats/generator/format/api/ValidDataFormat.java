package com.endava.cats.generator.format.api;

import io.swagger.v3.oas.models.media.Schema;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ValidDataFormat extends DataFormat<ValidDataFormatGenerator> {


    @Inject
    public ValidDataFormat(Instance<ValidDataFormatGenerator> generators) {
        super(generators);
    }

    public Object generate(Schema<?> schema, String propertyName) {
        return super.getGenerator(schema, propertyName).orElse(new VoidGenerator()).generate(schema);
    }

}
