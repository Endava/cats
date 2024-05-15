package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class IdnHostnameGeneratorTest {

    @Test
    void shouldGenerate() {
        IdnHostnameGenerator hostnameGenerator = new IdnHostnameGenerator();
        Assertions.assertThat(hostnameGenerator.generate(new Schema<>()).toString()).startsWith("www.ë").endsWith(".com");
    }

    @ParameterizedTest
    @CsvSource({"idn-hostname,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        IdnHostnameGenerator hostnameGenerator = new IdnHostnameGenerator();
        Assertions.assertThat(hostnameGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
