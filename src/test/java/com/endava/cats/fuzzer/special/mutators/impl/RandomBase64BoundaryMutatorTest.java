package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.generator.simple.Base64BoundaryGenerator;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

@QuarkusTest
class RandomBase64BoundaryMutatorTest {
    private RandomBase64BoundaryMutator mutator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        mutator = new RandomBase64BoundaryMutator();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(mutator.description()).contains("Base64", "padding");
    }

    @Test
    void shouldMutateFieldWithSeededRandomBase64BoundaryPayloads() {
        String inputJson = "{\"payload\":\"dGVzdA==\"}";
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            String mutated = mutator.mutate(inputJson, "payload");
            Assertions.assertThat(mutated).isNotEqualTo(inputJson);
            generated.add(mutated);
        }
        Assertions.assertThat(generated).hasSizeGreaterThan(10);
        Assertions.assertThat(Base64BoundaryGenerator.randomBoundaryValue()).hasSizeGreaterThanOrEqualTo(16);
    }
}
