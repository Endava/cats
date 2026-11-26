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
class OrcidGeneratorTest {
    private OrcidGenerator generator;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        generator = new OrcidGenerator();
    }

    @ParameterizedTest
    @CsvSource({
            "orcid,,true", "ORCID,,true",
            ",orcid,true", ",author#orcid,true", ",orcidId,true", ",orcid_id,true",
            "other,other,false", ",taxId,false"
    })
    void shouldApply(String format, String property, boolean expected) {
        Assertions.assertThat(generator.appliesTo(format == null ? "" : format, property == null ? "" : property))
                .isEqualTo(expected);
    }

    @Test
    void shouldGenerateValidOrcidWithCheckDigit() {
        Assertions.assertThat(OrcidGenerator.computeCheckDigit("000000021825009")).isEqualTo('7');
        for (int i = 0; i < 20; i++) {
            String orcid = generator.generate(new Schema<>()).toString();
            Assertions.assertThat(orcid).matches("^0000-\\d{4}-\\d{4}-\\d{3}[\\dX]$");
            String digits = orcid.replace("-", "");
            Assertions.assertThat(digits.charAt(15)).isEqualTo(OrcidGenerator.computeCheckDigit(digits.substring(0, 15)));
        }
    }

    @Test
    void shouldReturnInvalidValuesAndFormats() {
        Assertions.assertThat(generator.getAlmostValidValue()).isEqualTo("0000-0002-1825-0098");
        Assertions.assertThat(generator.getTotallyWrongValue()).isEqualTo("INVALID_ORCID");
        Assertions.assertThat(generator.matchingFormats()).containsExactly("orcid");
    }
}
