package com.endava.cats.model.generator.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class URITemplateGeneratorTest {

    @Test
    void shouldGenerate() {
        URITemplateGenerator uriTemplateGenerator = new URITemplateGenerator();
        Assertions.assertThat(uriTemplateGenerator.generate(new Schema<>())).isEqualTo("/fuzzing/{path}");
    }

    @ParameterizedTest
    @CsvSource({"uri-template,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        URITemplateGenerator uriTemplateGenerator = new URITemplateGenerator();
        Assertions.assertThat(uriTemplateGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
