package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ExtremePositiveValueInIntegerFieldsFuzzerTest {
    private ExtremePositiveValueInIntegerFieldsFuzzer extremePositiveValueInIntegerFields;

    @BeforeEach
    void setup() {
        extremePositiveValueInIntegerFields = new ExtremePositiveValueInIntegerFieldsFuzzer(null, null, null, null);
    }

    @Test
    void givenANewExtremePositiveValueIntegerFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheIntegerFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(extremePositiveValueInIntegerFields.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(IntegerSchema.class))).isTrue();
        Assertions.assertThat(extremePositiveValueInIntegerFields.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(extremePositiveValueInIntegerFields.description()).isNotNull();
        Assertions.assertThat(extremePositiveValueInIntegerFields.typeOfDataSentToTheService()).isNotNull();
    }
}
