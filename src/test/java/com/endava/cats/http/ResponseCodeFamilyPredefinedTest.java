package com.endava.cats.http;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class ResponseCodeFamilyPredefinedTest {


    @ParameterizedTest
    @CsvSource(value = {"200,true", "201,true", "202,true", "204,true", "400,true", "500,true", "000,false", "2000,false", "A00,false", "100,true", "600,false", "null,false", "099,false"}, nullValues = "null")
    void shouldCheckValidCodes(String code, boolean expected) {
        Assertions.assertThat(ResponseCodeFamily.isValidCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"100", "101", "200", "201", "300", "301", "400", "500", "501", "000"})
    void givenA3CharacterCode_whenParsingIt_thenTheCorrectResponseCodeFamilyIsReturned(String code) {
        ResponseCodeFamily responseCodeFamily = ResponseCodeFamilyPredefined.from(code);

        Assertions.assertThat(responseCodeFamily.getStartingDigit()).isEqualTo(String.valueOf(code.charAt(0)));
        Assertions.assertThat(responseCodeFamily.asString()).isEqualTo(code.charAt(0) + "XX");
    }

    @Test
    void givenA3600Code_whenParsingIt_thenTheDefaultZeroFamilyIsReturned() {
        ResponseCodeFamily actual = ResponseCodeFamilyPredefined.from("600");
        Assertions.assertThat(actual).isEqualTo(ResponseCodeFamilyPredefined.ZEROXX);
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
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX.asString()).isEqualTo("4XX");
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX.getStartingDigit()).isEqualTo("4");
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX.allowedResponseCodes()).containsOnly("400", "413", "414", "422", "431");
    }

    @Test
    void shouldBeValid4xxAAAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_AA.asString()).isEqualTo("401, 403");
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_AA.getStartingDigit()).isEqualTo("4");
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_AA.allowedResponseCodes()).containsOnly("403", "401");
    }

    @Test
    void shouldBeValid4xxMTAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_MT.asString()).isEqualTo("4XX");
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_MT.getStartingDigit()).isEqualTo("4");
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_MT.allowedResponseCodes()).containsOnly("406", "415");
    }

    @Test
    void shouldBeValid4xxNFAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_NF_AND_VALIDATION.asString()).isEqualTo("4XX");
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_NF_AND_VALIDATION.getStartingDigit()).isEqualTo("4");
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_NF_AND_VALIDATION.allowedResponseCodes()).containsOnly("404", "400", "422");
    }

    @Test
    void shouldBeValid4xxGeneric() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_GENERIC.asString()).isEqualTo("4XX");
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_GENERIC.getStartingDigit()).isEqualTo("4");
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_GENERIC.allowedResponseCodes()).containsOnly("4XX");
    }

    @Test
    void shouldBeValid2xxGeneric() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.TWOXX_GENERIC.asString()).isEqualTo("2XX");
        Assertions.assertThat(ResponseCodeFamilyPredefined.TWOXX_GENERIC.getStartingDigit()).isEqualTo("2");
        Assertions.assertThat(ResponseCodeFamilyPredefined.TWOXX_GENERIC.allowedResponseCodes()).containsOnly("2XX");
    }

    @Test
    void shouldBeValid5xxGeneric() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.FIVEXX_GENERIC.asString()).isEqualTo("5XX");
        Assertions.assertThat(ResponseCodeFamilyPredefined.FIVEXX_GENERIC.getStartingDigit()).isEqualTo("5");
        Assertions.assertThat(ResponseCodeFamilyPredefined.FIVEXX_GENERIC.allowedResponseCodes()).containsOnly("5XX");
    }

    @Test
    void shouldBeValid2xxAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.TWOXX.allowedResponseCodes()).containsOnly("200", "201", "202", "204");
    }

    @Test
    void shouldBeValid5xxAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.FIVEXX.allowedResponseCodes()).containsOnly("500", "501");
    }

    @Test
    void shouldBeValid1xxAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.ONEXX.allowedResponseCodes()).containsOnly("100", "101", "1XX");
    }

    @Test
    void shouldBeValid3xxAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.THREEXX.allowedResponseCodes()).containsOnly("300", "301", "302", "3XX");
    }

    @Test
    void shouldBeValid0xxAllowedCodes() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.ZEROXX.allowedResponseCodes()).containsOnly("000");
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
        ResponseCodeFamily family = ResponseCodeFamilyPredefined.from(respCodeFamily);
        boolean actualResult = family.matchesAllowedResponseCodes(expectedCode);

        Assertions.assertThat(actualResult).isEqualTo(expectedResult);
    }

    @Test
    void shouldContain400And501() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOUR00_FIVE01.allowedResponseCodes()).containsOnly("400", "501");
    }

    @Test
    void shouldHave400501AsString() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOUR00_FIVE01.asString()).isEqualTo("400|501");
    }

    @Test
    void shouldHave2XX4XXAsString() {
        Assertions.assertThat(ResponseCodeFamilyPredefined.FOURXX_TWOXX.asString()).isEqualTo("4XX|2XX");
    }

    @ParameterizedTest
    @CsvSource({"true,were", "false,were not"})
    void shouldReturnProperWordingBasedOnRequired(boolean required, String expected) {
        Object[] result = ResponseCodeFamilyPredefined.getExpectedWordingBasedOnRequiredFields(required);

        Assertions.assertThat(result[1]).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"431,true", "432,false"})
    void shouldReturnTooManyHeaders(int code, boolean result) {
        Assertions.assertThat(ResponseCodeFamily.isTooManyHeaders(code)).isEqualTo(result);
    }
}
