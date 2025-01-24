package com.endava.cats.generator.format.impl;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class CarrierCodeGeneratorTest {
    @Test
    void shouldGenerate() {
        CarrierCodeGenerator carrierCodeGenerator = new CarrierCodeGenerator();
        Assertions.assertThat(carrierCodeGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"iata,true", "icao,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        CarrierCodeGenerator carrierCodeGenerator = new CarrierCodeGenerator();
        Assertions.assertThat(carrierCodeGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"carrierCode,true", "CARRIERCODE,true", "iata_code,true", "icao_code,true", "carrierCode,true", "carrier_code,true", "other#carrierCode,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        CarrierCodeGenerator carrierCodeGenerator = new CarrierCodeGenerator();
        Assertions.assertThat(carrierCodeGenerator.appliesTo("", property)).isEqualTo(expected);
    }

    @Test
    void shouldGenerateIcaoCodeWhenLength3() {
        CarrierCodeGenerator carrierCodeGenerator = new CarrierCodeGenerator();
        Schema<String> schema = new Schema<>();
        schema.setMaxLength(3);
        Assertions.assertThat(carrierCodeGenerator.generate(schema).toString()).hasSize(3);
    }

    @Test
    void shouldGenerateIcoCodeWhenPattern3() {
        CarrierCodeGenerator carrierCodeGenerator = new CarrierCodeGenerator();
        Schema<String> schema = new Schema<>();
        schema.setPattern("^[A-Z]{3}$");
        Assertions.assertThat(carrierCodeGenerator.generate(schema).toString()).hasSize(3);
    }

    @Test
    void shouldGenerateIataCodeOtherwise() {
        CarrierCodeGenerator carrierCodeGenerator = new CarrierCodeGenerator();
        Schema<String> schema = new Schema<>();
        Assertions.assertThat(carrierCodeGenerator.generate(schema).toString()).hasSize(2);
    }
}
