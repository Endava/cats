package com.endava.cats.http;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
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
    @CsvSource({"100", "101", "200", "201", "300", "301", "400", "500", "501", "000"})
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
        Assertions.assertThat(ResponseCodeFamily.FOURXX_AA.asString()).isEqualTo("401, 403");
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
    void shouldBeValid4xxNFAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamily.FOURXX_NF.asString()).isEqualTo("4XX");
        Assertions.assertThat(ResponseCodeFamily.FOURXX_NF.getStartingDigit()).isEqualTo("4");
        Assertions.assertThat(ResponseCodeFamily.FOURXX_NF.allowedResponseCodes()).containsOnly("404");
    }

    @Test
    void shouldBeValid4xxGeneric() {
        Assertions.assertThat(ResponseCodeFamily.FOURXX_GENERIC.asString()).isEqualTo("4XX");
        Assertions.assertThat(ResponseCodeFamily.FOURXX_GENERIC.getStartingDigit()).isEqualTo("4");
        Assertions.assertThat(ResponseCodeFamily.FOURXX_GENERIC.allowedResponseCodes()).containsOnly("4XX");
    }

    @Test
    void shouldBeValid2xxGeneric() {
        Assertions.assertThat(ResponseCodeFamily.TWOXX_GENERIC.asString()).isEqualTo("2XX");
        Assertions.assertThat(ResponseCodeFamily.TWOXX_GENERIC.getStartingDigit()).isEqualTo("2");
        Assertions.assertThat(ResponseCodeFamily.TWOXX_GENERIC.allowedResponseCodes()).containsOnly("2XX");
    }

    @Test
    void shouldBeValid5xxGeneric() {
        Assertions.assertThat(ResponseCodeFamily.FIVEXX_GENERIC.asString()).isEqualTo("5XX");
        Assertions.assertThat(ResponseCodeFamily.FIVEXX_GENERIC.getStartingDigit()).isEqualTo("5");
        Assertions.assertThat(ResponseCodeFamily.FIVEXX_GENERIC.allowedResponseCodes()).containsOnly("5XX");
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
        Assertions.assertThat(ResponseCodeFamily.ONEXX.allowedResponseCodes()).containsOnly("100", "101", "1XX");
    }

    @Test
    void shouldBeValid3xxAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamily.THREEXX.allowedResponseCodes()).containsOnly("300", "301", "302", "3XX");
    }

    @Test
    void shouldBeValid0xxAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamily.ZEROXX.allowedResponseCodes()).containsOnly("000");
    }

    @ParameterizedTest
    @CsvSource({"100,100", "101,1XX", "2XX,200", "2XX,2XX"})
    void shouldMatchAsResponseCodeOrRange(String code1, String code2) {
        Assertions.assertThat(ResponseCodeFamily.matchAsCodeOrRange(code1, code2)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"100,101", "101,2XX", "2XX,100", "1XX,2XX"})
    void shouldNotMatchAsResponseCodeOrRange(String code1, String code2) {
        Assertions.assertThat(ResponseCodeFamily.matchAsCodeOrRange(code1, code2)).isFalse();
    }

    @Test
    void shouldReturnIs4xxTrue() {
        Assertions.assertThat(ResponseCodeFamily.is4xxCode(432)).isTrue();
    }

    @Test
    void shouldReturnIs4xxFalse() {
        Assertions.assertThat(ResponseCodeFamily.is4xxCode(222)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"200,201,true", "200,400,false", "2XX,202,true", "4XX,202,false"})
    void shouldMatchAsRangeOrGeneric(String respCodeFamily, String expectedCode, boolean expectedResult) {
        ResponseCodeFamily family = ResponseCodeFamily.from(respCodeFamily);
        boolean actualResult = family.matchesAllowedResponseCodes(expectedCode);

        Assertions.assertThat(actualResult).isEqualTo(expectedResult);
    }

    @Test
    void shouldContain400And501() {
        Assertions.assertThat(ResponseCodeFamily.FOUR00_FIVE01.allowedResponseCodes()).containsOnly("400", "501");
    }

    @Test
    void shouldHave400501AsString() {
        Assertions.assertThat(ResponseCodeFamily.FOUR00_FIVE01.asString()).isEqualTo("400|501");
    }

    @ParameterizedTest
    @CsvSource({"true,were", "false,were not"})
    void shouldReturnProperWordingBasedOnRequired(boolean required, String expected) {
        Object[] result = ResponseCodeFamily.getExpectedWordingBasedOnRequiredFields(required);

        Assertions.assertThat(result[1]).isEqualTo(expected);
    }
}
