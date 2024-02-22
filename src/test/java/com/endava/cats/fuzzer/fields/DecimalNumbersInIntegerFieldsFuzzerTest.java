package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DecimalNumbersInIntegerFieldsFuzzerTest {

    private DecimalNumbersInIntegerFieldsFuzzer decimalNumbersInIntegerFieldsFuzzer;

    @BeforeEach
    void setup() {
        decimalNumbersInIntegerFieldsFuzzer = new DecimalNumbersInIntegerFieldsFuzzer(null, null, null);
    }

    @Test
    void givenANewDecimalFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheDecimalFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(decimalNumbersInIntegerFieldsFuzzer.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("integer"))).isTrue();
        Assertions.assertThat(decimalNumbersInIntegerFieldsFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(decimalNumbersInIntegerFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(decimalNumbersInIntegerFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(decimalNumbersInIntegerFieldsFuzzer.getBoundaryValue(nrSchema)).isInstanceOf(Double.class);
    }
}
