package com.endava.cats.model.generator.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class URIReferenceGeneratorTest {

    @Test
    void shouldGenerate() {
        URIReferenceGenerator uriReferenceGenerator = new URIReferenceGenerator();
        Assertions.assertThat(uriReferenceGenerator.generate(new Schema<>())).isEqualTo("/fuzzing/");
    }

    @ParameterizedTest
    @CsvSource({"uri-reference,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        URIReferenceGenerator uriReferenceGenerator = new URIReferenceGenerator();
        Assertions.assertThat(uriReferenceGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
