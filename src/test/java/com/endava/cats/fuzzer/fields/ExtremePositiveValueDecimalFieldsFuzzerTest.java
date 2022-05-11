package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ExtremePositiveValueDecimalFieldsFuzzerTest {

    private ExtremePositiveValueDecimalFieldsFuzzer extremePositiveValueDecimalFieldsFuzzer;

    @BeforeEach
    void setup() {
        extremePositiveValueDecimalFieldsFuzzer = new ExtremePositiveValueDecimalFieldsFuzzer(null, null, null, null);
    }

    @Test
    void givenANewExtremePositiveValueDecimalFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheDecimalFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(extremePositiveValueDecimalFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(NumberSchema.class))).isTrue();
        Assertions.assertThat(extremePositiveValueDecimalFieldsFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(extremePositiveValueDecimalFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(extremePositiveValueDecimalFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
