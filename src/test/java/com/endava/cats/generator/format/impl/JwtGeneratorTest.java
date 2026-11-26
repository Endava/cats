package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class JwtGeneratorTest {
    private JwtGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new JwtGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "jwt,,true", "jws,,true",
            ",jwt,true", ",auth#jwtToken,true", ",idToken,true", ",accessToken,true", ",refresh_token,true",
            "other,other,false", ",password,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateThreeSegmentBase64UrlJwt() {
        String jwt = generator.generate(new Schema<>()).toString();
        Assertions.assertThat(jwt).matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue().split("\\.")).hasSize(2);
        Assertions.assertThat(generator.getTotallyWrongValue()).contains("+", "/", "=");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("jwt", "jws");
    }
}
