package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.model.CatsHeader;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@QuarkusTest
class RandomProtocolAndOverrideHeadersMutatorTest {
    private RandomProtocolAndOverrideHeadersMutator mutator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        mutator = new RandomProtocolAndOverrideHeadersMutator();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(mutator.description()).isNotBlank();
    }

    @Test
    void shouldInjectOrOverrideProtocolHeaders() {
        Set<CatsHeader> initial = Set.of(
                CatsHeader.builder().name("X-Custom").value("1").build(),
                CatsHeader.builder().name("If-Match").value("\"old\"").build());

        for (int i = 0; i < 25; i++) {
            Collection<CatsHeader> mutated = mutator.mutate(initial);
            Assertions.assertThat(mutated)
                    .extracting(header -> header.getName() + ":" + header.getValue())
                    .isNotEqualTo(List.of("X-Custom:1", "If-Match:\"old\""));
        }
    }
}
