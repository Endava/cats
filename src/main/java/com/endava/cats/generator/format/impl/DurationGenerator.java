package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.Random;

@Singleton
public class DurationGenerator implements ValidDataFormatGenerator {
    private final Random random = new Random();

    @Override
    public Object generate(Schema<?> schema) {
        return Duration.ofDays(random.nextInt());
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "duration".equalsIgnoreCase(format);
    }
}
