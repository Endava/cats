package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.RandomStringUtils;

import javax.inject.Singleton;

@Singleton
public class HostnameGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator {
    @Override
    public Object generate(Schema<?> schema) {
        String generated = RandomStringUtils.randomAlphabetic(5);
        return "www.cats%s.com".formatted(generated);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "hostname".equalsIgnoreCase(format);
    }

    @Override
    public String getAlmostValidValue() {
        return "cool.cats.";
    }

    @Override
    public String getTotallyWrongValue() {
        return "aaa111-aaaaa---";
    }
}
