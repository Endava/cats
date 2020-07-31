package com.endava.cats.generator.simple;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class StringGeneratorTest {

    @Test
    void shouldGenerateLargeString() {
        String actual = StringGenerator.generateLargeString(2);
        String expected = StringGenerator.FUZZ + StringGenerator.FUZZ;

        Assertions.assertThat(actual).isEqualTo(expected);
    }

    @Test
    void shouldGenerateRandomString() {
        String actual = StringGenerator.generateRandomString();
        Assertions.assertThat(actual).isEqualTo(StringGenerator.FUZZ);
    }

    @Test
    void givenAPatternThatDoesNotHaveLength_whenGeneratingARandomString_thenTheLenghtIsProperlyAdded() {
        String actual = StringGenerator.generate("[A-Z]", 3, 3);
        Assertions.assertThat(actual).hasSize(3);
    }


    @Test
    void givenAPatternThatDoesHaveLength_whenGeneratingARandomString_thenTheLenghtIsProperlyAdded() {
        String actual = StringGenerator.generate("[A-Z]{3}", 4, 4);
        Assertions.assertThat(actual).hasSize(3);
    }

    @Test
    void givenASchemaWithMaxLength_whenGeneratingARightBoundaryString_thenTheGeneratedStringHasProperLength() {
        Schema schema = new StringSchema();
        int maxLength = 10;
        schema.setMaxLength(maxLength);

        String actual = StringGenerator.generateRightBoundString(schema);

        Assertions.assertThat(actual.length()).isGreaterThan(maxLength + 10);
    }

    @Test
    void givenASchemaWithoutMaxLength_whenGeneratingARightBoundaryString_thenTheGeneratedStringHasDefaultLength() {
        Schema schema = new StringSchema();

        String actual = StringGenerator.generateRightBoundString(schema);
        Assertions.assertThat(actual.length()).isGreaterThanOrEqualTo(StringGenerator.DEFAULT_MAX_LENGTH);
    }

    @Test
    void givenASchemaWithMinLength_whenGeneratingALeftBoundaryString_thenTheGeneratedStringHasProperLength() {
        Schema schema = new StringSchema();
        int minLength = 10;
        schema.setMinLength(minLength);

        String actual = StringGenerator.generateLeftBoundString(schema);

        Assertions.assertThat(actual.length()).isLessThan(minLength);
    }

    @Test
    void givenASchemaWithoutMinLength_whenGeneratingALeftBoundaryString_thenTheGeneratedStringHasDefaultLength() {
        Schema schema = new StringSchema();

        String actual = StringGenerator.generateLeftBoundString(schema);
        Assertions.assertThat(actual).isEmpty();
    }

    @Test
    void givenAPatternEndingWith$_whenSanitizing_thenThe$IsRemoved() {
        String pattern = "[A-Z]$";
        String expected = "[A-Z]";

        String actual = StringGenerator.sanitize(pattern);
        Assertions.assertThat(actual).isEqualTo(expected);
    }

    @Test
    void givenAPatternEndingWithPlus_whenSanitizing_thenThePlusIsRemoved() {
        String pattern = "[A-Z]+";
        String expected = "[A-Z]";

        String actual = StringGenerator.sanitize(pattern);
        Assertions.assertThat(actual).isEqualTo(expected);
    }

    @Test
    void givenAPatternEndingWithAsterisk_whenSanitizing_thenTheAsteriskIsRemoved() {
        String pattern = "[A-Z]*";
        String expected = "[A-Z]";

        String actual = StringGenerator.sanitize(pattern);
        Assertions.assertThat(actual).isEqualTo(expected);
    }

    @Test
    void givenAPatternWithoutAnyStartingOrEndingSigns_whenSanitizing_thenTheSamePatternIsReturned() {
        String pattern = "[A-Z]";
        String expected = "[A-Z]";

        String actual = StringGenerator.sanitize(pattern);
        Assertions.assertThat(actual).isEqualTo(expected);
    }

    @Test
    void givenAPatternStartingWithCircumflex_whenSanitizing_thenTheCircumflexIsRemoved() {
        String pattern = "^[A-Z]";
        String expected = "[A-Z]";

        String actual = StringGenerator.sanitize(pattern);
        Assertions.assertThat(actual).isEqualTo(expected);
    }

    @Test
    void givenAPredefinedPattern_whenSanitizing_thenTheAlphanumericPatternIsReturned() {
        String pattern = "^(?!\\s*$).+";
        String expected = "[a-zA-Z0-9]";

        String actual = StringGenerator.sanitize(pattern);
        Assertions.assertThat(actual).isEqualTo(expected);
    }

}
