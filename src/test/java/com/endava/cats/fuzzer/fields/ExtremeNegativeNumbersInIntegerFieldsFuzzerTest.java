package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ExtremeNegativeNumbersInIntegerFieldsFuzzerTest {

    private ExtremeNegativeNumbersInIntegerFieldsFuzzer extremeNegativeNumbersInIntegerFieldsFuzzer;

    @BeforeEach
    void setup() {
        extremeNegativeNumbersInIntegerFieldsFuzzer = new ExtremeNegativeNumbersInIntegerFieldsFuzzer(null, null, null, null);
    }

    @Test
    void givenANewExtremeNegativeValueIntegerFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheIntegerFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(extremeNegativeNumbersInIntegerFieldsFuzzer.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("integer"))).isTrue();
        Assertions.assertThat(extremeNegativeNumbersInIntegerFieldsFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(extremeNegativeNumbersInIntegerFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(extremeNegativeNumbersInIntegerFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(extremeNegativeNumbersInIntegerFieldsFuzzer.getBoundaryValue(nrSchema)).isInstanceOf(Long.class);
    }
}
