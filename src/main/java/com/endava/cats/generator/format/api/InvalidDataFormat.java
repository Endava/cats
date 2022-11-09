package com.endava.cats.generator.format.api;

import com.endava.cats.generator.format.impl.VoidGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Keeps a mapping between OpenAPI format types and CATs generators
 */
@Singleton
public class InvalidDataFormat extends DataFormat<InvalidDataFormatGenerator> {
    @Inject
    public InvalidDataFormat(Instance<InvalidDataFormatGenerator> generators) {
        super(generators);
    }

    public InvalidDataFormatGenerator generator(Schema<?> schema, String propertyName) {
        return super.getGenerator(schema, propertyName).orElse(new VoidGenerator());
    }
}
