package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class CommonMarkGeneratorTest {
    private CommonMarkGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new CommonMarkGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "commonmark,,true", "markdown,,true",
            ",commonmark,true", ",doc#markdown,true",
            "html,,false", ",description,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateMarkdownText() {
        Assertions.assertThat(generator.generate(new Schema<>()).toString())
                .startsWith("# ")
                .contains("**CommonMark**");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("commonmark", "markdown");
    }
}
