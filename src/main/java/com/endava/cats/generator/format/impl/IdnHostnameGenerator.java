package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

/**
 * A generator class implementing interfaces for generating valid IDN (Internationalized Domain Name) hostname data formats.
 * It implements the ValidDataFormatGenerator, InvalidDataFormatGenerator, and OpenAPIFormat interfaces.
 */
@Singleton
public class IdnHostnameGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.catsFaker().ancient().titan().toLowerCase(Locale.ROOT);

        return "www.ë%s.com".formatted(generated);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "idn-hostname".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("idn-hostname");
    }

    @Override
    public String getAlmostValidValue() {
        return "-ëcats..com";
    }

    @Override
    public String getTotallyWrongValue() {
        return "invalid idn host!";
    }
}
