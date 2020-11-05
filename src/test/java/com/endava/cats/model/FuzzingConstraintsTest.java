package com.endava.cats.model;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FuzzingConstraintsTest {

    private FuzzingConstraints fuzzingConstraints;

    @Test
    void shouldHaveMinLength_whenHasMinLengthIsTrue() {
        fuzzingConstraints = FuzzingConstraints.builder().hasMinlength(true).hasRequiredFieldsFuzzed(false).build();

        Assertions.assertThat(fuzzingConstraints.hasMinLengthOrMandatoryFieldsFuzzed()).isTrue();
        Assertions.assertThat(fuzzingConstraints.getRequiredString()).isEqualTo("FALSE but has minLength");
    }

    @Test
    void shouldHaveMinLength_whenHasMinLengthIsFalseAndHasRequiredFieldsFuzzedIsTrue() {
        fuzzingConstraints = FuzzingConstraints.builder().hasMinlength(false).hasRequiredFieldsFuzzed(true).build();

        Assertions.assertThat(fuzzingConstraints.hasMinLengthOrMandatoryFieldsFuzzed()).isTrue();
        Assertions.assertThat(fuzzingConstraints.getRequiredString()).isEqualTo("TRUE");
    }

    @Test
    void shouldHaveMinLength_whenHasMinLengthIsTrueAndHasRequiredFieldsFuzzedIsTrue() {
        fuzzingConstraints = FuzzingConstraints.builder().hasMinlength(true).hasRequiredFieldsFuzzed(true).build();

        Assertions.assertThat(fuzzingConstraints.hasMinLengthOrMandatoryFieldsFuzzed()).isTrue();
        Assertions.assertThat(fuzzingConstraints.getRequiredString()).isEqualTo("TRUE");
    }

    @Test
    void shouldReturnHasMinLengthFalse() {
        fuzzingConstraints = FuzzingConstraints.builder().hasMinlength(false).hasRequiredFieldsFuzzed(false).build();
        Assertions.assertThat(fuzzingConstraints.hasMinLengthOrMandatoryFieldsFuzzed()).isFalse();
    }
}
