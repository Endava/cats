package com.endava.cats.generator.format.api;

import com.endava.cats.generator.format.impl.VoidGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Singleton;

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
