package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class Gtin13GeneratorTest {

    @Test
    void shouldGenerate() {
        Gtin13Generator gtin13Generator = new Gtin13Generator();
        Assertions.assertThat(gtin13Generator.generate(new Schema<>()).toString()).matches("[0-9]+").hasSize(13);
    }

    @ParameterizedTest
    @CsvSource({"gtin-13,not,true", "gtin13,not,true", "not,global-trade-item-number,true", "not,global-trade-number,true", "not,not,false",
            "ean13,not,true", "not,european-article-number,true"})
    void shouldApply(String format, String property, boolean expected) {
        Gtin13Generator gtin13Generator = new Gtin13Generator();
        Assertions.assertThat(gtin13Generator.appliesTo(format, property)).isEqualTo(expected);
    }
}
