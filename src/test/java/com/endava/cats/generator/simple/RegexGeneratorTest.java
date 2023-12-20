package com.endava.cats.generator.simple;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

@QuarkusTest
class RegexGeneratorTest {

    @Test
    void shouldReturnDefaultWhenMaxLessThanZero() {
        String result = RegexGenerator.generate(Pattern.compile("test"), "test", 10, -10);

        Assertions.assertThat(result).isEqualTo(RegexGenerator.DEFAULT);
    }

    @Test
    void shouldReturnPrefixWhenPrefixMatchesPatternAndMinLessThanZero() {
        String result = RegexGenerator.generate(Pattern.compile("test"), "test", -10, 10);

        Assertions.assertThat(result).isEqualTo("test");
    }

    @Test
    void shouldReturnDefaultWhenPrefixMatchesPatternButNotLength() {
        String result = RegexGenerator.generate(Pattern.compile("test"), "test", 10, 10);

        Assertions.assertThat(result).isEqualTo("DEFAULT_CATS");
    }

    @Test
    void shouldGenerateFromRegexAndSize() {
        String result = RegexGenerator.generate(Pattern.compile("test+"), "test", 10, 10);

        Assertions.assertThat(result).isEqualTo("testtttttttttt");
    }
}
