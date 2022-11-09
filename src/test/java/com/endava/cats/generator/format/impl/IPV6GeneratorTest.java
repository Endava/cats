package com.endava.cats.generator.format.impl;


import com.endava.cats.generator.format.impl.IPV6Generator;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class IPV6GeneratorTest {

    private IPV6Generator ipv6Generator;

    @BeforeEach
    void setup() {
        ipv6Generator = new IPV6Generator();
    }

    @ParameterizedTest
    @CsvSource({"not,ipv6,true", "ipv6,not,true", "not,not,false"})
    void shouldRecognizeIpV6Address(String format, String property, boolean expected) {
        boolean isIp = ipv6Generator.appliesTo(format, property);
        Assertions.assertThat(isIp).isEqualTo(expected);
    }

    @Test
    void givenAIPV6FormatGeneratorStrategy_whenGettingTheAlmostValidValue_thenTheValueIsReturnedAsExpected() {
        Assertions.assertThat(ipv6Generator.getAlmostValidValue()).isEqualTo("2001:db8:85a3:8d3:1319:8a2e:370:99999");
    }

    @Test
    void givenAIPV6FormatGeneratorStrategy_whenGettingTheTotallyWrongValue_thenTheValueIsReturnedAsExpected() {
        Assertions.assertThat(ipv6Generator.getTotallyWrongValue()).isEqualTo("2001:db8:85a3");
    }
}
