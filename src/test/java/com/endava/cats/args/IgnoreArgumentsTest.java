package com.endava.cats.args;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

@ExtendWith(SpringExtension.class)
class IgnoreArgumentsTest {

    private IgnoreArguments ignoreArguments;

    @BeforeEach
    void setup() {
        ignoreArguments = new IgnoreArguments();
    }

    @ParameterizedTest
    @CsvSource(value = {"null", "'", "'   '", "empty"}, nullValues = {"null"})
    void shouldReturnFalseForProvidedIgnoredCodes(String codes) {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", codes);

        Assertions.assertThat(ignoreArguments.isEmptyIgnoredResponseCodes()).isTrue();
    }

    @Test
    void shouldReturnIgnoreHttpCodes() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", "200,4XX");
        List<String> ignoredCodes = ignoreArguments.getIgnoreResponseCodes();

        Assertions.assertThat(ignoredCodes).containsOnly("200", "4XX");
    }

    @Test
    void shouldReturnEmptyIgnoreHttpCodes() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", "empty");
        List<String> ignoredCodes = ignoreArguments.getIgnoreResponseCodes();

        Assertions.assertThat(ignoredCodes).isEmpty();
    }

    @Test
    void shouldReturnFalseIgnoreUndocumentedCode() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodeUndocumentedCheck", "empty");
        boolean testCasesPresent = ignoreArguments.isIgnoreResponseCodeUndocumentedCheck();

        Assertions.assertThat(testCasesPresent).isFalse();
    }

    @Test
    void shouldReturnIgnoreResponseBodyCheck() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseBodyCheck", "true");
        boolean testCasesPresent = ignoreArguments.isIgnoreResponseBodyCheck();

        Assertions.assertThat(testCasesPresent).isTrue();
    }

    @Test
    void shouldReturnIgnoreUndocumentedCode() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodeUndocumentedCheck", "true");
        boolean testCasesPresent = ignoreArguments.isIgnoreResponseCodeUndocumentedCheck();

        Assertions.assertThat(testCasesPresent).isTrue();
    }


    @Test
    void shouldReturnFalseIgnoreResponseBodyCheck() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseBodyCheck", "empty");
        boolean testCasesPresent = ignoreArguments.isIgnoreResponseBodyCheck();

        Assertions.assertThat(testCasesPresent).isFalse();
    }

    @Test
    void shouldReturnTrueIsBlackbox() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseBodyCheck", "true");
        boolean testCasesPresent = ignoreArguments.isIgnoreResponseBodyCheck();

        Assertions.assertThat(testCasesPresent).isTrue();
    }

    @Test
    void shouldReturnFalseIsBlackbox() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseBodyCheck", "empty");
        boolean testCasesPresent = ignoreArguments.isIgnoreResponseBodyCheck();

        Assertions.assertThat(testCasesPresent).isFalse();
    }

    @Test
    void shouldMatchIgnoredResponseCodes() {
        ReflectionTestUtils.setField(ignoreArguments, "ignoreResponseCodes", "2XX,400");
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("200")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("202")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("400")).isTrue();
        Assertions.assertThat(ignoreArguments.isIgnoredResponseCode("404")).isFalse();
    }
}
