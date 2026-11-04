package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class DuplicateJsonFieldKeyMutatorTest {
    private DuplicateJsonFieldKeyMutator mutator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        mutator = new DuplicateJsonFieldKeyMutator();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(mutator.description()).isNotBlank();
    }

    @Test
    void shouldDuplicateTopLevelAndNestedFieldKeys() {
        String topLevel = "{\"role\":\"USER\",\"count\":1}";
        String mutatedTop = mutator.mutate(topLevel, "role");
        Assertions.assertThat(mutatedTop)
                .isNotEqualTo(topLevel)
                .contains("\"role\":\"USER\",\"role\":");

        String nested = "{\"user\":{\"role\":\"USER\"},\"items\":[{\"code\":\"A\"}]}";
        String mutatedNested = mutator.mutate(nested, "user#role");
        Assertions.assertThat(mutatedNested)
                .contains("\"role\":\"USER\",\"role\":");

        String mutatedArrayChild = mutator.mutate(nested, "items#code");
        Assertions.assertThat(mutatedArrayChild)
                .contains("\"code\":\"A\",\"code\":");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "'', role",
            "'{\"role\":\"USER\"}', NULL",
            "'{\"role\":\"USER\"}', ''",
            "'not-json', role"
    })
    void shouldReturnOriginalWhenInputOrFieldInvalid(String payload, String field) {
        Assertions.assertThat(mutator.mutate(payload, field)).isEqualTo(payload);
    }
}
