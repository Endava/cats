package com.endava.cats.generator.simple;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class StringGeneratorTest {

    @Test
    public void shouldGenerateLargeString() {
        String actual = StringGenerator.generateLargeString(2);
        String expected = StringGenerator.FUZZ + StringGenerator.FUZZ;

        Assertions.assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void shouldGenerateRandomString() {
        String actual = StringGenerator.generateRandomString();
        Assertions.assertThat(actual).isEqualTo(StringGenerator.FUZZ);
    }

    @Test
    public void givenASchemaWithMaxLength_whenGeneratingARightBoundaryString_thenTheGeneratedStringHasProperLength() {
        Schema<?> schema = new StringSchema();
        int maxLength = 10;
        schema.setMaxLength(maxLength);

        String actual = StringGenerator.generateRightBoundString(schema);

        Assertions.assertThat(actual.length() >= maxLength + 10).isTrue();
    }

    @Test
    public void givenASchemaWithoutMaxLength_whenGeneratingARightBoundaryString_thenTheGeneratedStringHasDefaultLength() {
        Schema<?> schema = new StringSchema();

        String actual = StringGenerator.generateRightBoundString(schema);
        Assertions.assertThat(actual.length() >= StringGenerator.DEFAULT_MAX_LENGTH).isTrue();
    }

    @Test
    public void givenASchemaWithMinLength_whenGeneratingALeftBoundaryString_thenTheGeneratedStringHasProperLength() {
        Schema<?> schema = new StringSchema();
        int minLength = 10;
        schema.setMinLength(minLength);

        String actual = StringGenerator.generateLeftBoundString(schema);

        Assertions.assertThat(actual.length() <= minLength).isTrue();
    }

    @Test
    public void givenASchemaWithoutMinLength_whenGeneratingALeftBoundaryString_thenTheGeneratedStringHasDefaultLength() {
        Schema<?> schema = new StringSchema();

        String actual = StringGenerator.generateLeftBoundString(schema);
        Assertions.assertThat(actual.length() == 0).isTrue();
    }
}
