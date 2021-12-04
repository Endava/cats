package com.endava.cats.generator.simple;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
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

        Assertions.assertThat(actual.length()).isGreaterThan(maxLength + 10 - 1);
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
    void shouldReturnLongLengthWhenMaxLengthIsIntegerMax() {
        Schema<String> schema = new StringSchema();
        schema.setMaxLength(Integer.MAX_VALUE - 2);
        int maxExpected = Integer.MAX_VALUE - 2;
        long actual = StringGenerator.getRightBoundaryLength(schema);

        Assertions.assertThat(actual).isEqualTo(maxExpected);
    }
}
