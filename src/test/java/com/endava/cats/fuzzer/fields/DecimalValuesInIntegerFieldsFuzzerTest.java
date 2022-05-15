package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

@QuarkusTest
class DecimalValuesInIntegerFieldsFuzzerTest {

    private DecimalValuesInIntegerFieldsFuzzer decimalValuesInIntegerFieldsFuzzer;

    @BeforeEach
    void setup() {
        decimalValuesInIntegerFieldsFuzzer = new DecimalValuesInIntegerFieldsFuzzer(null, null, null, null);
    }

    @Test
    void givenANewDecimalFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheDecimalFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(decimalValuesInIntegerFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(IntegerSchema.class))).isTrue();
        Assertions.assertThat(decimalValuesInIntegerFieldsFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(decimalValuesInIntegerFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(decimalValuesInIntegerFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(decimalValuesInIntegerFieldsFuzzer.getBoundaryValue(nrSchema)).isInstanceOf(Double.class);
    }
}
