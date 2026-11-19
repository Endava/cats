package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

@QuarkusTest
class RandomCrossFieldSwapAndSkewMutatorTest {
    private RandomCrossFieldSwapAndSkewMutator mutator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        mutator = new RandomCrossFieldSwapAndSkewMutator();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(mutator.description()).contains("swap", "bounds");
    }

    @Test
    void shouldMutateAcrossSiblingFieldsWithAndWithoutFuzzingData() {
        String multiFieldJson = "{\"min\":10,\"max\":20,\"label\":\"active\"}";
        FuzzingData data = FuzzingData.builder().allFieldsByHttpMethod(Set.of("min", "max", "label")).build();

        for (int i = 0; i < 15; i++) {
            Assertions.assertThat(mutator.mutate(multiFieldJson, "min")).isNotEqualTo(multiFieldJson);
            Assertions.assertThat(mutator.mutate(multiFieldJson, "min", data)).isNotEqualTo(multiFieldJson);
        }
    }

    @Test
    void shouldHandleSingleFieldAndInvalidPayloadsGracefully() {
        String singleFieldJson = "{\"onlyOne\":\"value\"}";
        Assertions.assertThat(mutator.mutate(singleFieldJson, "onlyOne"))
                .isNotEqualTo(singleFieldJson)
                .contains("primary", "confirm");
        Assertions.assertThat(mutator.mutate("", "onlyOne")).isEmpty();
        Assertions.assertThat(mutator.mutate(singleFieldJson, "")).isEqualTo(singleFieldJson);
    }
}
