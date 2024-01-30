package com.endava.cats.generator.format.api;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InvalidDataFormatTest {

    @Inject
    InvalidDataFormat invalidDataFormat;
    @Test
    void shouldReturnVoidWhenNoFormat() {
        Assertions.assertThat(invalidDataFormat.generator(new Schema<>(), null)).isInstanceOf(VoidGenerator.class);
    }
}
