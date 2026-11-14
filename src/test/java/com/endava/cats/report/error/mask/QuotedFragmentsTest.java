package com.endava.cats.report.error.mask;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class QuotedFragmentsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Cannot read properties of undefined (reading 'id') | Cannot read properties of undefined (reading 'id')",
            "KeyError: 'name' | KeyError: 'name'",
            "Cannot invoke \"java.util.Map.get(Object)\" because \"map\" is null | Cannot invoke \"java.util.Map.get(Object)\" because \"map\" is null",
            "to required type 'java.lang.Double' | to required type 'java.lang.Double'",
            "Unexpected token 'a', \"a b\" is not valid JSON | Unexpected token 'STR', \"STR\" is not valid JSON",
            "Request method 'UNLOCK' not supported | Request method 'STR' not supported",
            "Content type 'application/cats' not supported | Content type 'STR' not supported",
            "Couldn't parse ' OR x | Couldn't parse ' OR x",
            "name: can't be blank; email: can't be blank | name: can't be blank; email: can't be blank",
            "value '<INPUT>' rejected | value '<INPUT>' rejected"
    })
    void shouldKeepCodeIdentifiersAndMaskQuotedData(String message, String expected) {
        Assertions.assertThat(QuotedFragments.maskData(message)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(quoteCharacter = '~', value = {
            "'id'), true",
            "\"String.length()\", true",
            "'Name', true",
            "'<INPUT>', false",
            "'NUM', false",
            "'STR', false",
            "reading, false",
            "'a', false"
    })
    void shouldPinOnlyQuotedCodeIdentifiers(String token, boolean pinned) {
        Assertions.assertThat(QuotedFragments.isPinned(token)).isEqualTo(pinned);
    }
}
