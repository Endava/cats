package com.endava.cats.fuzzer.special.mutators;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class StringMutationUtilsTest {

    @Test
    void testApplySameMutation() {
        String initial = "FAKE_FUZZ";
        String mutated = "FAKE_FUZZY";
        String target = "TEST_STRING";
        String expected = "TEST_STRINGY";

        String result = StringMutationUtils.applySameMutation(initial, mutated, target);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void testExtractMutatedString() {
        String initial = "FAKE_FUZZ";
        String text = "Some text containing FAKE\u205F　_FUZZ and other things";
        String expected = "FAKE\u205F　_FUZZ";

        String result = StringMutationUtils.extractMutatedString(initial, text);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void testExtractMutatedStringNoMatch() {
        String initial = "FAKE_FUZZ";
        String text = "Some text without the initial string";

        String result = StringMutationUtils.extractMutatedString(initial, text);
        assertThat(result).isNull();
    }

    @Test
    void testApplySameMutationNoInsertions() {
        String initial = "FAKE_FUZZ";
        String mutated = "FAKE_FUZZ";
        String target = "TEST_STRING";
        String expected = "TEST_STRING";

        String result = StringMutationUtils.applySameMutation(initial, mutated, target);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void testApplySameMutationWithMultipleInsertions() {
        String initial = "FAKE_FUZZ";
        String mutated = "FAKE_X_YYYFUZZ";
        String target = "TEST_STRING";
        String expected = "TEST_SX_YYYTRING";

        String result = StringMutationUtils.applySameMutation(initial, mutated, target);
        assertThat(result).isEqualTo(expected);
    }
}
