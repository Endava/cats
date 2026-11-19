package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

@QuarkusTest
class RandomUnicodeNormalizationMutatorTest {
    private RandomUnicodeNormalizationMutator mutator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        mutator = new RandomUnicodeNormalizationMutator();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(mutator.description()).contains("NFKC", "Best-Fit");
    }

    @Test
    void shouldMutateFieldWithAndWithoutFuzzingDataSchema() {
        String inputJson = "{\"username\":\"admin\"}";
        Schema<String> boundedSchema = new Schema<String>().type("string").minLength(4).maxLength(12);
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Map.of("username", boundedSchema)).build();
        for (int i = 0; i < 10; i++) {
            Assertions.assertThat(mutator.mutate(inputJson, "username")).isNotEqualTo(inputJson);
            Assertions.assertThat(mutator.mutate(inputJson, "username", data)).isNotEqualTo(inputJson);
        }
    }
}
