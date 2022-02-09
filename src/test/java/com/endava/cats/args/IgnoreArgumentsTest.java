package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

@QuarkusTest
class IgnoreArgumentsTest {

    private IgnoreArguments ignoreArguments;

    @BeforeEach
    void setup() {
        ignoreArguments = new IgnoreArguments();
    }

    @Test
    void shouldReturnIgnoreHttpCodes() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", List.of("200", "4XX"));
        List<String> ignoredCodes = ignoreArguments.getIgnoreResponseCodes();

        Assertions.assertThat(ignoredCodes).containsOnly("200", "4XX");
    }

    @Test
    void shouldMatchIgnoredResponseCodes() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", List.of("2XX", "400"));
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("200")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("202")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("400")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("404")).isFalse();
    }

    @Test
    void shouldMatchIgnoredResponseCodesWhenBlackbox() {
        ReflectionTestUtils.setField(ignoreArguments, "blackbox", true);
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("200")).isTrue();
    }

    @Test
    void shouldNotMatchIgnoredResponseCodesWhenBlackbox() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", List.of("4xx"));
        ReflectionTestUtils.setField(ignoreArguments, "blackbox", false);
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("200")).isFalse();
    }
}
