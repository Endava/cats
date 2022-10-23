package com.endava.cats.model.generator.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class HostnameGeneratorTest {

    @Test
    void shouldGenerate() {
        HostnameGenerator hostnameGenerator = new HostnameGenerator();
        Assertions.assertThat(hostnameGenerator.generate(new Schema<>())).isEqualTo("www.endava.com");
    }

    @ParameterizedTest
    @CsvSource({"hostname,true", "other,false"})
    void shouldApply(String format, boolean expected) {
        HostnameGenerator hostnameGenerator = new HostnameGenerator();
        Assertions.assertThat(hostnameGenerator.appliesTo(format, "")).isEqualTo(expected);
    }
}
