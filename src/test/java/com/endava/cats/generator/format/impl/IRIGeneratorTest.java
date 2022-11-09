package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.impl.IRIGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class IRIGeneratorTest {

    @Test
    void shouldGenerate() {
        IRIGenerator iriGenerator = new IRIGenerator();
        Assertions.assertThat(iriGenerator.generate(new Schema<>())).isEqualTo("http://ëxample.com/cats");
    }

    @ParameterizedTest
    @CsvSource({"iri,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        IRIGenerator iriGenerator = new IRIGenerator();
        Assertions.assertThat(iriGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
