package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class FullNameGeneratorTest {

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
    }
    @Test
    void shouldGenerate() {
        FullNameGenerator fullNameGenerator = new FullNameGenerator();
        Assertions.assertThat(fullNameGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"fullName,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        FullNameGenerator fullNameGenerator = new FullNameGenerator();
        Assertions.assertThat(fullNameGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"fullName,true", "FULLNAME,true", "fullName,true", "full_name,true", "other#fullName,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        FullNameGenerator fullNameGenerator = new FullNameGenerator();
        Assertions.assertThat(fullNameGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
