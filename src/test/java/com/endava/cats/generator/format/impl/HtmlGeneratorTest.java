package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class HtmlGeneratorTest {
    private HtmlGenerator generator;

    @BeforeEach
    void setup() {
        generator = new HtmlGenerator();
    }

    @ParameterizedTest
    @CsvSource({"html,,true", "HTML,,true", ",html,false", "commonmark,,false"})
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateHtmlContent() {
        Assertions.assertThat(generator.generate(new Schema<>()).toString()).isEqualTo("<p>Sample HTML content</p>");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("html");
    }
}
