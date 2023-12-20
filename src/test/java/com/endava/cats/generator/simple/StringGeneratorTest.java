package com.endava.cats.generator.simple;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        Assertions.assertThat(actual).startsWith(StringGenerator.FUZZ);
    }

    @Test
    void givenAPatternThatDoesNotHaveLength_whenGeneratingARandomString_thenTheLengthIsProperlyAdded() {
        String actual = StringGenerator.generate("[A-Z]+", 3, 3);
        Assertions.assertThat(actual).hasSize(3);
    }


    @Test
    void givenAPatternThatDoesHaveLength_whenGeneratingARandomString_thenTheLengthIsProperlyAdded() {
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

    @ParameterizedTest
    @CsvSource({"0,0,10,30", "0,20,10,30"})
    void shouldGenerateValueWhenPatternAndNoMinMax(int min, int max, int left, int right) {
        String generated = StringGenerator.generate("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z$", min, max);
        Assertions.assertThat(generated).hasSizeBetween(left, right);
    }


    @ParameterizedTest
    @CsvSource({"[A-Z]+", "^[^\\s]+(\\s+[^\\s]+)*$", "^[\\w\\u00C0-\\u02AF]+(\\s+[\\w\\u00C0-\\u02AF]+)*$"})
    void shouldGenerateStringForSpecificRegexes(String regex) {
        String generated = StringGenerator.generate(regex, 2048, 2048);

        Assertions.assertThat(generated).matches(regex).hasSizeBetween(2048, 2048);
    }

    @ParameterizedTest
    @CsvSource({"te`st,test", "boost,boost"})
    void shouldSanitize(String input, String expected) {
        String sanitized = StringGenerator.sanitize(input);
        Assertions.assertThat(sanitized).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"^\\+?[1-9]\\d{6,15}$;16", "[A-Z]+;20", "[A-Z0-9]{13,18};18", "[0-9]+;10", "^(?=[^\\s])(?=.*[^\\s]$)(?=^(?:(?!<|>|%3e|%3c).)*$).*$;2048"}, delimiterString = ";")
    void shouldGenerateFixedLength(String pattern, int length) {
        String fixedLengthGenerated = StringGenerator.generateExactLength(pattern, length);

        Assertions.assertThat(fixedLengthGenerated).hasSize(length).matches(pattern);
    }

    @Test
    void shouldGenerateWhenNegativeLength() {
        String generated = StringGenerator.generate("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$", -1, -1);
        Assertions.assertThat(generated).hasSize(17).matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    }
}
