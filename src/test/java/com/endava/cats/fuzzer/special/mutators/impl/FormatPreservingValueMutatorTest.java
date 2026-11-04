package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class FormatPreservingValueMutatorTest {
    private FormatPreservingValueMutator mutator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        mutator = new FormatPreservingValueMutator();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(mutator.description()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({
            "'{\"id\":\"550e8400-e29b-41d4-a716-446655440000\"}', id",
            "'{\"createdAt\":\"2025-03-15T10:20:30Z\"}', createdAt",
            "'{\"active\":true}', active",
            "'{\"count\":42}', count",
            "'{\"zero\":0}', zero",
            "'{\"name\":\"AliceSmith\"}', name",
            "'{\"digits\":\"12345\"}', digits",
            "'{\"empty\":\"\"}', empty"
    })
    void shouldMutateValueWhilePreservingJsonValidity(String inputJson, String field) {
        for (int i = 0; i < 10; i++) {
            String mutated = mutator.mutate(inputJson, field);
            Assertions.assertThat(mutated).isNotEqualTo(inputJson);
        }
    }

    @Test
    void shouldHandleNotSetAndNullShape() {
        Assertions.assertThat(FormatPreservingValueMutator.mutatePreservingShape("NOT_SET"))
                .isEqualTo("00000000-0000-0000-0000-000000000000");
        Assertions.assertThat(FormatPreservingValueMutator.mutatePreservingShape(null))
                .isEqualTo("00000000-0000-0000-0000-000000000000");
    }
}
