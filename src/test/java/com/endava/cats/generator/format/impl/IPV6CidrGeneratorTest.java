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
class IPV6CidrGeneratorTest {
    private IPV6CidrGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new IPV6CidrGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "ipv6-cidr,,true", "ipv6Cidr,,true", "ipv6_cidr,,true",
            ",ipv6Cidr,true", ",network#ipv6Range,true", ",ipv6Subnet,true",
            "cidr,,false", "ipv4-cidr,,false", "ipv6,,false", ",networkCidr,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidIpv6Cidr() {
        String value = generator.generate(new Schema<>()).toString();
        Assertions.assertThat(value).matches("^2001:db8:[0-9a-f]{1,4}::/(32|48|56|64)$");
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("2001:db8::/129");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("gggg::/999");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("ipv6-cidr", "ipv6Cidr", "ipv6_cidr");
    }
}
