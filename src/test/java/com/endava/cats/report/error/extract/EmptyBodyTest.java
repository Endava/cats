package com.endava.cats.report.error.extract;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class EmptyBodyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "[Empty response body]"})
    void shouldTreatBlankBodiesAsEmpty(String body) {
        assertThat(EmptyBody.isEmpty(body)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "x", "[Empty response body] but not really"})
    void shouldKeepBodiesWithContent(String body) {
        assertThat(EmptyBody.isEmpty(body)).isFalse();
    }
}
