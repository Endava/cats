package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.List;

/**
 * A generator class implementing various interfaces for generating valid and invalid duration data formats.
 * It also implements the OpenAPIFormat interface.
 */
@Singleton
public class DurationGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return Duration.ofDays(CatsRandom.instance().nextInt(0, 99999));
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "duration".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("duration");
    }

    @Override
    public String getAlmostValidValue() {
        return "PT2334384";
    }

    @Override
    public String getTotallyWrongValue() {
        return "1234569";
    }
}
