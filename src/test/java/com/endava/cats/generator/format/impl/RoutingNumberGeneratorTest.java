package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class RoutingNumberGeneratorTest {
    private RoutingNumberGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new RoutingNumberGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "aba,,true", "routingNumber,,true", "routing-number,,true", "routing_number,,true", "rtn,,true",
            ",routingNumber,true", ",bank#routing_number,true", ",abaRoutingNumber,true", ",transitNumber,true",
            "other,other,false", ",accountNumber,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidRoutingNumberWithChecksum() {
        Assertions.assertThat(RoutingNumberGenerator.computeCheckDigit("12210515")).isEqualTo(5);
        int[] weights = {3, 7, 1, 3, 7, 1, 3, 7, 1};
        for (int i = 0; i < 20; i++) {
            String rtn = generator.generate(new Schema<>()).toString();
            Assertions.assertThat(rtn).matches("^[0-9]{9}$");
            int sum = 0;
            for (int j = 0; j < 9; j++) {
                sum += weights[j] * (rtn.charAt(j) - '0');
            }
            Assertions.assertThat(sum % 10).isZero();
        }
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("122105156");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("999999999");
        Assertions.assertThat(generator.matchingFormats()).contains("aba", "routingNumber", "routing-number", "rtn");
    }
}
