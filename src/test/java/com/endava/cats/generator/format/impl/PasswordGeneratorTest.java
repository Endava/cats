package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.impl.PasswordGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class PasswordGeneratorTest {

    @Test
    void shouldGenerate() {
        PasswordGenerator passwordGenerator = new PasswordGenerator();
        Assertions.assertThat(passwordGenerator.generate(new Schema<>())).isEqualTo("catsISc00l?!useIt#");
    }

    @ParameterizedTest
    @CsvSource({"password,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        PasswordGenerator passwordGenerator = new PasswordGenerator();
        Assertions.assertThat(passwordGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @Test
    void givenAPasswordFormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        PasswordGenerator strategy = new PasswordGenerator();
        Assertions.assertThat(strategy.getAlmostValidValue()).isEqualTo("bgZD89DEkl");
    }

    @Test
    void givenAPasswordFormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        PasswordGenerator strategy = new PasswordGenerator();
        Assertions.assertThat(strategy.getTotallyWrongValue()).isEqualTo("abcdefgh");
    }
}
