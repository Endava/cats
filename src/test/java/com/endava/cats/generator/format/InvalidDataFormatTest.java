package com.endava.cats.generator.format;

import com.endava.cats.generator.format.api.InvalidDataFormat;
import com.endava.cats.generator.format.impl.VoidGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

@QuarkusTest
class InvalidDataFormatTest {

    @Inject
    InvalidDataFormat invalidDataFormat;
    @Test
    void shouldReturnVoidWhenNoFormat() {
        Assertions.assertThat(invalidDataFormat.generator(new Schema<>(), null)).isInstanceOf(VoidGenerator.class);
    }
}
