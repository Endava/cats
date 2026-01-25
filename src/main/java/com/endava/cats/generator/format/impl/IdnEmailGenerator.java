package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

/**
 * A generator class implementing interfaces for generating valid IDN (Internationalized Domain Name) email data formats.
 * It implements the ValidDataFormatGenerator and OpenAPIFormat interfaces.
 */
@Singleton
public class IdnEmailGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        return CatsUtil.catsFaker().ancient().primordial().toLowerCase(Locale.ROOT) + ".cööl.cats@cats.io";
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "idn-email".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("idn-email");
    }
}
