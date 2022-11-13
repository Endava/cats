package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class KvPairsGenerator implements ValidDataFormatGenerator {

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "kvpairs".equalsIgnoreCase(format);
    }

    @Override
    public Object generate(Schema<?> schema) {
        return Map.of("key", "value", "anotherKey", "anotherValue");
    }
    // path, name, unsigned int, unixtime,
}
