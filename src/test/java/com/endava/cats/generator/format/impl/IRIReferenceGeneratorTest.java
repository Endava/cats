package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.impl.IRIReferenceGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class IRIReferenceGeneratorTest {

    @Test
    void shouldGenerate() {
        IRIReferenceGenerator iriReferenceGenerator = new IRIReferenceGenerator();
        Assertions.assertThat(iriReferenceGenerator.generate(new Schema<>())).isEqualTo("/füzzing/");
    }

    @ParameterizedTest
    @CsvSource({"iri-reference,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        IRIReferenceGenerator iriReferenceGenerator = new IRIReferenceGenerator();
        Assertions.assertThat(iriReferenceGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
