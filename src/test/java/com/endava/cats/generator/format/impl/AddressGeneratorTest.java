package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class AddressGeneratorTest {

    @Test
    void shouldGenerate() {
        com.endava.cats.util.CatsRandom.initRandom(0);
        AddressGenerator addressGenerator = new AddressGenerator();
        Assertions.assertThat(addressGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(5);
    }

    @ParameterizedTest
    @CsvSource({"contractAddress", "owner_address", "walletAddress"})
    void shouldGenerateHexAddressForBlockchainProperties(String property) {
        com.endava.cats.util.CatsRandom.initRandom(0);
        AddressGenerator addressGenerator = new AddressGenerator();
        Assertions.assertThat(addressGenerator.appliesTo("", property)).isTrue();
        Assertions.assertThat(addressGenerator.generate(new Schema<>()).toString()).matches("^0x[0-9a-f]{40}$");
    }

    @ParameterizedTest
    @CsvSource({"address,true", "other,false", "emailAddress,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        AddressGenerator addressGenerator = new AddressGenerator();
        Assertions.assertThat(addressGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"address,true", "ADDRESS,true", "other,false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        AddressGenerator addressGenerator = new AddressGenerator();
        Assertions.assertThat(addressGenerator.appliesTo("", property)).isEqualTo(expected);
    }
}
