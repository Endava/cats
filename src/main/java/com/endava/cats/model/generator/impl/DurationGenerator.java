package com.endava.cats.model.generator.impl;

import com.endava.cats.model.generator.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.Random;

@Singleton
public class DurationGenerator implements ValidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        return Duration.ofDays(new Random().nextInt());
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "duration".equalsIgnoreCase(format);
    }
}
