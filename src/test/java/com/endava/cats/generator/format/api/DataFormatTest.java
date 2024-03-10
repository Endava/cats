package com.endava.cats.generator.format.api;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DataFormatTest {

    @Test
    void shouldMatchWhenPatternAndMaxLengthNull() {
        String generated = "test";
        Object result = DataFormat.matchesPatternOrNull(new Schema<>(), generated);

        Assertions.assertThat(result).asString().isEqualTo(generated);
    }

    @Test
    void shouldMatchWhenPatternMatches() {
        String generated = "test";
        Schema<?> schema = new Schema<>();
        schema.setPattern("[a-z]+");
        Object result = DataFormat.matchesPatternOrNull(schema, generated);

        Assertions.assertThat(result).asString().isEqualTo(generated);
    }

    @Test
    void shouldReturnNullWhenNotMatchingPattern() {
        String generated = "test";
        Schema<?> schema = new Schema<>();
        schema.setPattern("[A-Z]+");
        Object result = DataFormat.matchesPatternOrNull(schema, generated);

        Assertions.assertThat(result).isNull();
    }

    @Test
    void shouldMatchWhenPatternNullAndLessThanMaxSize() {
        String generated = "test";
        Schema<?> schema = new Schema<>();
        schema.setMaxLength(10);
        Object result = DataFormat.matchesPatternOrNull(schema, generated);

        Assertions.assertThat(result).asString().isEqualTo(generated);
    }

    @Test
    void shouldReturnNullWhenLargerThanMaxSize() {
        String generated = "test";
        Schema<?> schema = new Schema<>();
        schema.setMaxLength(2);
        Object result = DataFormat.matchesPatternOrNull(schema, generated);

        Assertions.assertThat(result).isNull();
    }


}
