package com.endava.cats.fuzzer.special.mutators;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class StringMutationUtilsTest {
    
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

    @ParameterizedTest
    @CsvSource({"FAKE_FUZZ,FAKE_FUZZY,TEST_STRING,TEST_STRINGY", "FAKE_FUZZ,FAKE_FUZZ,TEST_STRING,TEST_STRING", "FAKE_FUZZ,FAKE_X_YYYFUZZ,TEST_STRING,TEST_SX_YYYTRING"})
    void testApplySameMutation(String initial, String mutated, String target, String expected) {
        String result = StringMutationUtils.applySameMutation(initial, mutated, target);
        assertThat(result).isEqualTo(expected);
    }
}
