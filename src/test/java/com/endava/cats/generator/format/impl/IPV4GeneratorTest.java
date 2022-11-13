package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class IPV4GeneratorTest {

    private IPV4Generator ipv4Generator;

    @BeforeEach
    void setup() {
        ipv4Generator = new IPV4Generator();
    }

    @ParameterizedTest
    @CsvSource({"not,ip,true", "not,ipaddress,true", "not,hostIp,true", "ip,something,true", "ipv4,something,true", "not,something,false"})
    void shouldRecognizeIpAddress(String format, String property, boolean expected) {
        boolean isIp = ipv4Generator.appliesTo(format, property);
        Assertions.assertThat(isIp).isEqualTo(expected);
    }

    @Test
    void shouldGenerate() {
        Assertions.assertThat(ipv4Generator.generate(new Schema<>()).toString()).matches("[0-9]+.[0-9]+.[0-9]+.[0-9]+");
    }

    @Test
    void givenAIPV4FormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        Assertions.assertThat(ipv4Generator.getAlmostValidValue()).isEqualTo("10.10.10.300");
    }

    @Test
    void givenAIPV4FormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        Assertions.assertThat(ipv4Generator.getTotallyWrongValue()).isEqualTo("255.");
    }
}
