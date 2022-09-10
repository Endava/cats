package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

@QuarkusTest
class ExtremeNegativeNumbersInDecimalFieldsFuzzerTest {
    private ExtremeNegativeNumbersInDecimalFieldsFuzzer extremeNegativeNumbersInDecimalFieldsFuzzer;

    @BeforeEach
    void setup() {
        extremeNegativeNumbersInDecimalFieldsFuzzer = new ExtremeNegativeNumbersInDecimalFieldsFuzzer(null, null, null, null);
    }

    @Test
    void givenANewExtremeNegativeValueDecimalFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheDecimalFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(extremeNegativeNumbersInDecimalFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(NumberSchema.class))).isTrue();
        Assertions.assertThat(extremeNegativeNumbersInDecimalFieldsFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(extremeNegativeNumbersInDecimalFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(extremeNegativeNumbersInDecimalFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(extremeNegativeNumbersInDecimalFieldsFuzzer.getBoundaryValue(nrSchema)).isInstanceOf(BigDecimal.class);
    }
}

