package com.endava.cats.fuzzer.http;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ResponseCodeFamilyTest {


    @Test
    void givenA3DigitsCode_whenCallingIsValidCode_thenTheCodeIsValid() {
        Assertions.assertThat(ResponseCodeFamily.isValidCode("200")).isTrue();
    }

    @Test
    void givenA4DigitsCode_whenCallingIsValidCode_thenTheCodeIsNotValid() {
        Assertions.assertThat(ResponseCodeFamily.isValidCode("2000")).isFalse();
    }

    @Test
    void givenA3CharactersCodeThatDoesNotStartWithADigit_whenCallingIsValidCode_thenTheCodeIsNotValid() {
        Assertions.assertThat(ResponseCodeFamily.isValidCode("A00")).isFalse();
    }

    @Test
    void givenA3CharacterCode_whenParsingIt_thenTheCorrectResponseCodeFamilyIsReturned() {
        String[] codes = new String[]{"100", "101", "200", "201", "300", "301", "400", "401", "500", "501"};

        for (String code : codes) {
            ResponseCodeFamily responseCodeFamily = ResponseCodeFamily.from(code);

            Assertions.assertThat(responseCodeFamily.getStartingDigit()).isEqualTo(String.valueOf(code.charAt(0)));
            Assertions.assertThat(responseCodeFamily.asString()).isEqualTo(code.charAt(0) + "XX");
        }
    }

    @Test
    void givenA3600Code_whenParsingIt_thenTheDefaultZeroFamilyIsReturned() {
        Assertions.assertThat(ResponseCodeFamily.from("600")).isEqualTo(ResponseCodeFamily.ZEROXX);
    }

    @Test
    void givenA200Code_whenCheckingIfIs2xxFamily_thenItReturnsTrue() {
        Assertions.assertThat(ResponseCodeFamily.is2xxCode(200)).isTrue();
    }

    @Test
    void givenA300Code_whenCheckingIfIs2xxFamily_thenItReturnsFalse() {
        Assertions.assertThat(ResponseCodeFamily.is2xxCode(300)).isFalse();
    }

    @Test
    void givenA501Code_whenCheckingIfIsUnimplemented_thenItReturnsTrue() {
        Assertions.assertThat(ResponseCodeFamily.isUnimplemented(501)).isTrue();
    }

    @Test
    void givenA502Code_whenCheckingIfIsUnimplemented_thenItReturnsFalse() {
        Assertions.assertThat(ResponseCodeFamily.isUnimplemented(502)).isFalse();
    }
}
