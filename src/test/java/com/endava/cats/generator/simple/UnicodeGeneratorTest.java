package com.endava.cats.generator.simple;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class UnicodeGeneratorTest {

    @Test
    void shouldGenerateUnicodeMatchingPredicate() {
        String generated = UnicodeGenerator.generateRandomUnicodeString(10, Character::isISOControl);

        Assertions.assertThat(generated).matches(string -> string.chars().allMatch(Character::isISOControl));
    }
}
