package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RandomRawNumericAndRegexMutatorsTest {

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
    }

    @Test
    void shouldSpliceRawNumericLexemesWithoutQuotingNumbers() {
        RandomRawNumericAndLexemeMutator rawMutator = new RandomRawNumericAndLexemeMutator();
        Assertions.assertThat(rawMutator.description()).contains("lexemes");

        String input = "{\"amount\":5,\"keep\":\"ok\"}";
        for (int i = 0; i < 20; i++) {
            String mutated = rawMutator.mutate(input, "amount");
            Assertions.assertThat(mutated).isNotEqualTo(input).contains("\"keep\":\"ok\"");
        }
        Assertions.assertThat(rawMutator.mutate("", "amount")).isEmpty();
        Assertions.assertThat(RandomRawNumericAndLexemeMutator.spliceRawLexeme("not-json", "amount", "1e10000"))
                .isEqualTo("not-json");
    }

    @Test
    void shouldMutateFieldWithRegexCompilerAndAnchorPayloads() {
        RandomRegexAndPatternCrashMutator regexMutator = new RandomRegexAndPatternCrashMutator();
        Assertions.assertThat(regexMutator.description()).contains("Regexp#to_s", "backreferences");

        String input = "{\"pattern\":\"abc\"}";
        for (int i = 0; i < 15; i++) {
            Assertions.assertThat(regexMutator.mutate(input, "pattern")).isNotEqualTo(input);
        }
    }
}
