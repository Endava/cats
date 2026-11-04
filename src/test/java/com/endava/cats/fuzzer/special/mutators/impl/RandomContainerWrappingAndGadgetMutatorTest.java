package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RandomContainerWrappingAndGadgetMutatorTest {
    private RandomContainerWrappingAndGadgetMutator mutator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        mutator = new RandomContainerWrappingAndGadgetMutator();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(mutator.description()).isNotBlank();
    }

    @Test
    void shouldWrapFieldAndNestedFieldAcrossAllStrategies() {
        String inputJson = "{\"id\":\"123\",\"nested\":{\"code\":42}}";
        for (int i = 0; i < 20; i++) {
            Assertions.assertThat(mutator.mutate(inputJson, "id")).isNotEqualTo(inputJson);
            Assertions.assertThat(mutator.mutate(inputJson, "nested#code")).isNotEqualTo(inputJson);
        }
        Assertions.assertThat(RandomContainerWrappingAndGadgetMutator.extractLeafKey("")).isEqualTo("value");
        Assertions.assertThat(RandomContainerWrappingAndGadgetMutator.extractLeafKey(null)).isEqualTo("value");
    }
}
