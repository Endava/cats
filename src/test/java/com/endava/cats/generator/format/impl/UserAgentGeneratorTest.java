package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class UserAgentGeneratorTest {
    @Test
    void shouldGenerate() {
        UserAgentGenerator userAgentGenerator = new UserAgentGenerator();
        Assertions.assertThat(userAgentGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"userAgent,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        UserAgentGenerator userAgentGenerator = new UserAgentGenerator();
        Assertions.assertThat(userAgentGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"userAgent,true", "USERAGENT,true", "userAgent,true", "user_agent,true", "other#userAgent,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        UserAgentGenerator userAgentGenerator = new UserAgentGenerator();
        Assertions.assertThat(userAgentGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
