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
 * A generator class implementing interfaces for generating valid International Reference Numbers (IRN) data formats.
 * It implements the ValidDataFormatGenerator, InvalidDataFormatGenerator, and OpenAPIFormat interfaces.
 */
@Singleton
public class IRIGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    @Override
    public Object generate(Schema<?> schema) {
        String generated = CatsUtil.catsFaker().ancient().god().toLowerCase(Locale.ROOT);

        return "https://ë-%s.com/cats".formatted(generated);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "iri".equalsIgnoreCase(format);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("iri");
    }

    @Override
    public String getAlmostValidValue() {
        return "https://ë-cats..com/path with spaces";
    }

    @Override
    public String getTotallyWrongValue() {
        return "not an iri";
    }
}
