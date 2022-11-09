package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

@QuarkusTest
class DecimalFieldsRightBoundaryFuzzerTest {

    private DecimalFieldsRightBoundaryFuzzer decimalFieldsRightBoundaryFuzzer;

    @BeforeEach
    void setup() {
        decimalFieldsRightBoundaryFuzzer = new DecimalFieldsRightBoundaryFuzzer(null, null, null, null);
    }

    @Test
    void givenANewDecimalFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheDecimalFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(decimalFieldsRightBoundaryFuzzer.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("number"))).isTrue();
        Assertions.assertThat(decimalFieldsRightBoundaryFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(decimalFieldsRightBoundaryFuzzer.description()).isNotNull();
        Assertions.assertThat(decimalFieldsRightBoundaryFuzzer.getBoundaryValue(nrSchema)).isInstanceOf(BigDecimal.class);
    }
}