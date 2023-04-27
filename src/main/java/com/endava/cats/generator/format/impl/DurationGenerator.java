package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Random;

@Singleton
public class DurationGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private final Random random = new Random();

    @Override
    public Object generate(Schema<?> schema) {
        return Duration.ofDays(random.nextInt(0, 99999));
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "duration".equalsIgnoreCase(format);
    }

    @Override
    public List<String> marchingFormats() {
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
