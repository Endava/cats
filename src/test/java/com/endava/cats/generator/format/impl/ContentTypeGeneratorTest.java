package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class ContentTypeGeneratorTest {

    @Test
    void shouldGenerate() {
        ContentTypeGenerator contentTypeGenerator = new ContentTypeGenerator();
        Assertions.assertThat(contentTypeGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"contentType,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        ContentTypeGenerator contentTypeGenerator = new ContentTypeGenerator();
        Assertions.assertThat(contentTypeGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"contentType,true", "CONTENTTYPE,true", "content-type,true", "content_type,true",
            "content#type,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        ContentTypeGenerator contentTypeGenerator = new ContentTypeGenerator();
        Assertions.assertThat(contentTypeGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
