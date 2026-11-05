package com.endava.cats.command.triage;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

@QuarkusTest
class FailureIntentTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', quoteCharacter = '`', nullValues = "NONE", value = {
            "'billingSchedule' must not be null;REQUIRE",
            "tax number is required;REQUIRE",
            "Operator GT requires a numeric value;REQUIRE",
            "NOT expression must have exactly one child, found 2;REQUIRE",
            "field 'amount': null value not allowed;REQUIRE",
            "Shipping address must be null for DIGITAL orders;REMOVE",
            "Unrecognized field 'legacyField';REMOVE",
            "discount must not be provided for FREE plans;REMOVE",
            "Watchlist with name 'B' and type COUNTRY already exists;UNIQUE",
            "email must be unique;UNIQUE",
            "Invalid sort direction 'HYI'. Valid values: [DESC, ASC];NONE",
            "Invalid field length;NONE",
            "Unsupported operator 'gt';NONE"})
    void shouldClassifyWhatTheMessageDemands(String message, FailureIntent expected) {
        Assertions.assertThat(FailureIntent.of(message.toLowerCase(Locale.ROOT)).orElse(null)).isEqualTo(expected);
    }
}
