package com.endava.cats.fuzzer.http;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    void givenANullCode_whenCallingIsValidCode_thenTheCodeIsNotValid() {
        Assertions.assertThat(ResponseCodeFamily.isValidCode(null)).isFalse();
    }

    @Test
    void givenA3CharactersCodeThatDoesNotStartWithADigit_whenCallingIsValidCode_thenTheCodeIsNotValid() {
        Assertions.assertThat(ResponseCodeFamily.isValidCode("A00")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"100", "101", "200", "201", "300", "301", "400", "401", "500", "501"})
    void givenA3CharacterCode_whenParsingIt_thenTheCorrectResponseCodeFamilyIsReturned(String code) {
        ResponseCodeFamily responseCodeFamily = ResponseCodeFamily.from(code);

        Assertions.assertThat(responseCodeFamily.getStartingDigit()).isEqualTo(String.valueOf(code.charAt(0)));
        Assertions.assertThat(responseCodeFamily.asString()).isEqualTo(code.charAt(0) + "XX");
    }

    @Test
    void givenA3600Code_whenParsingIt_thenTheDefaultZeroFamilyIsReturned() {
        ResponseCodeFamily actual = ResponseCodeFamily.from("600");
        Assertions.assertThat(actual).isEqualTo(ResponseCodeFamily.ZEROXX);
        Assertions.assertThat(actual.asString()).isEqualTo("0XX");

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

    @Test
    void shouldBeValid4xxGenericAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamily.FOURXX.asString()).isEqualTo("4XX");
        Assertions.assertThat(ResponseCodeFamily.FOURXX.getStartingDigit()).isEqualTo("4");
        Assertions.assertThat(ResponseCodeFamily.FOURXX.allowedResponseCodes()).containsOnly("400", "413", "414", "422");
    }

    @Test
    void shouldBeValid4xxAAAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamily.FOURXX_AA.asString()).isEqualTo("4XX");
        Assertions.assertThat(ResponseCodeFamily.FOURXX_AA.getStartingDigit()).isEqualTo("4");
        Assertions.assertThat(ResponseCodeFamily.FOURXX_AA.allowedResponseCodes()).containsOnly("403", "401");
    }

    @Test
    void shouldBeValid4xxMTAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamily.FOURXX_MT.asString()).isEqualTo("4XX");
        Assertions.assertThat(ResponseCodeFamily.FOURXX_MT.getStartingDigit()).isEqualTo("4");
        Assertions.assertThat(ResponseCodeFamily.FOURXX_MT.allowedResponseCodes()).containsOnly("406", "415");
    }

    @Test
    void shouldBeValid2xxAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamily.TWOXX.allowedResponseCodes()).containsOnly("200", "201", "202", "204");
    }

    @Test
    void shouldBeValid5xxAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamily.FIVEXX.allowedResponseCodes()).containsOnly("500", "501");
    }

    @Test
    void shouldBeValid1xxAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamily.ONEXX.allowedResponseCodes()).containsOnly("100");
    }

    @Test
    void shouldBeValid3xxAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamily.THREEXX.allowedResponseCodes()).containsOnly("301", "302");
    }

    @Test
    void shouldBeValid0xxAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamily.ZEROXX.allowedResponseCodes()).containsOnly("000");
    }
}
