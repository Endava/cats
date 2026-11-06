package com.endava.cats.command.triage;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class FieldNamesTest {
    @ParameterizedTest
    @ValueSource(strings = {"a", "_", "$", "a0_$-", "a-b.c_d.$value", "items[*]", "items[*].quantity", "a[*].b[*].c[*]"})
    void shouldAcceptSupportedFieldPaths(String path) {
        assertThat(FieldNames.isValidPath(path)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".a", "a.", "a..b", "0a", "-a", "a.0b", "a[0]", "a[]", "a[**]", "a[*][*]", "a[*]b", "a b", "a\nb", "a#b", "é", "a.é"})
    void shouldRejectMalformedFieldPaths(String path) {
        assertThat(FieldNames.isValidPath(path)).isFalse();
    }

    @Test
    void shouldValidateDeepPathsWithoutOverflowingTheStack() {
        String prefix = "items[*].".repeat(50_000);

        assertThat(FieldNames.isValidPath(prefix + "value")).isTrue();
        assertThat(FieldNames.isValidPath(prefix)).isFalse();
        assertThat(FieldNames.isValidPath(prefix + "value[0]")).isFalse();
    }

    @Test
    void shouldValidateLongSegmentsWithoutOverflowingTheStack() {
        String segment = "a".repeat(100_000);

        assertThat(FieldNames.isValidPath(segment + "[*]")).isTrue();
        assertThat(FieldNames.isValidPath(segment + "!")).isFalse();
    }
}
