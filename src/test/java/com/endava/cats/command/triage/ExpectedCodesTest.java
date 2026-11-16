package com.endava.cats.command.triage;

import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.ExpectedResponse;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class ExpectedCodesTest {
    @Test
    void shouldPreferTypedCodesAndUseProseOnlyForLegacyTests() {
        CatsTestCase test = new CatsTestCase();
        test.setExpectedResult("Should return [2XX]");
        Assertions.assertThat(ExpectedCodes.from(test).allows(200)).isTrue();

        test.recordExpectedResponse(new ExpectedResponse(List.of("405")));
        Assertions.assertThat(ExpectedCodes.from(test).allows(405)).isTrue();
        Assertions.assertThat(ExpectedCodes.from(test).allows(200)).isFalse();

        test.recordExpectedResponse(new ExpectedResponse(List.of()));
        Assertions.assertThat(ExpectedCodes.from(test).isEmpty()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', quoteCharacter = '`', value = {
            "Should return [2XX];true;false;201;true",
            "Should return 2XX as required fields were not removed;true;false;204;true",
            "Should return [4XX|2XX];false;false;200;true",
            "Should return 4XX as required fields were removed;false;true;422;true",
            "Should return [400, 422];false;true;422;true",
            "Should return [400, 422];false;true;404;false",
            "Should return [200, 201];true;false;202;false",
            "Should return a response that doesn't match;false;false;200;false"})
    void shouldParseExpectedCodeFamiliesAndExactCodes(String expectedResult, boolean only2xx, boolean only4xx, int code, boolean allowed) {
        ExpectedCodes expected = ExpectedCodes.parse(expectedResult);

        Assertions.assertThat(expected.expectsOnly2xx()).isEqualTo(only2xx);
        Assertions.assertThat(expected.expectsOnly4xx()).isEqualTo(only4xx);
        Assertions.assertThat(expected.allows(code)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"NULL", "''"})
    void shouldBeEmptyWhenNoCodesArePresent(String expectedResult) {
        Assertions.assertThat(ExpectedCodes.parse(expectedResult).isEmpty()).isTrue();
    }
}
