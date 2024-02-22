package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ExtremePositiveNumbersInIntegerFieldsFuzzerTest {
    private ExtremePositiveNumbersInIntegerFieldsFuzzer extremePositiveValueInIntegerFields;

    @BeforeEach
    void setup() {
        extremePositiveValueInIntegerFields = new ExtremePositiveNumbersInIntegerFieldsFuzzer(null, null, null);
    }

    @Test
    void givenANewExtremePositiveValueIntegerFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheIntegerFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(extremePositiveValueInIntegerFields.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("integer"))).isTrue();
        Assertions.assertThat(extremePositiveValueInIntegerFields.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(extremePositiveValueInIntegerFields.description()).isNotNull();
        Assertions.assertThat(extremePositiveValueInIntegerFields.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(extremePositiveValueInIntegerFields.getBoundaryValue(nrSchema)).isInstanceOf(Long.class);
    }
}
