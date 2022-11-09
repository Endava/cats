package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

@QuarkusTest
class ExtremePositiveNumbersInDecimalFieldsFuzzerTest {

    private ExtremePositiveNumbersInDecimalFieldsFuzzer extremePositiveNumbersInDecimalFieldsFuzzer;

    @BeforeEach
    void setup() {
        extremePositiveNumbersInDecimalFieldsFuzzer = new ExtremePositiveNumbersInDecimalFieldsFuzzer(null, null, null, null);
    }

    @Test
    void givenANewExtremePositiveValueDecimalFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheDecimalFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(extremePositiveNumbersInDecimalFieldsFuzzer.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("number"))).isTrue();
        Assertions.assertThat(extremePositiveNumbersInDecimalFieldsFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(extremePositiveNumbersInDecimalFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(extremePositiveNumbersInDecimalFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(extremePositiveNumbersInDecimalFieldsFuzzer.getBoundaryValue(nrSchema)).isInstanceOf(BigDecimal.class);
    }
}
