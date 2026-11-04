package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RandomHomoglyphAndInvisibleCharsMutatorTest {
    private RandomHomoglyphAndInvisibleCharsMutator mutator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        mutator = new RandomHomoglyphAndInvisibleCharsMutator();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(mutator.description()).isNotBlank();
    }

    @Test
    void shouldMutateExistingStringWithHomoglyphsInvisibleCharsOrBidi() {
        String inputJson = "{\"status\":\"ACTIVE\",\"digits\":\"12345\"}";
        for (int i = 0; i < 15; i++) {
            Assertions.assertThat(mutator.mutate(inputJson, "status")).isNotEqualTo(inputJson);
            Assertions.assertThat(mutator.mutate(inputJson, "digits")).isNotEqualTo(inputJson);
        }
        Assertions.assertThat(RandomHomoglyphAndInvisibleCharsMutator.applyUnicodeMutation(""))
                .isNotBlank();
    }
}
