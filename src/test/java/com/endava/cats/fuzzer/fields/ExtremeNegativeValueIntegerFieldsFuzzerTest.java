package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.apache.commons.lang3.math.NumberUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ExtremeNegativeValueIntegerFieldsFuzzerTest {

    private ExtremeNegativeValueIntegerFieldsFuzzer extremeNegativeValueIntegerFieldsFuzzer;

    @BeforeEach
    void setup() {
        extremeNegativeValueIntegerFieldsFuzzer = new ExtremeNegativeValueIntegerFieldsFuzzer(null, null, null, null);
    }

    @Test
    void givenANewExtremeNegativeValueIntegerFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheIntegerFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(extremeNegativeValueIntegerFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(IntegerSchema.class))).isTrue();
        Assertions.assertThat(NumberUtils.isCreatable(extremeNegativeValueIntegerFieldsFuzzer.getBoundaryValue(nrSchema))).isTrue();
        Assertions.assertThat(extremeNegativeValueIntegerFieldsFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(extremeNegativeValueIntegerFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(extremeNegativeValueIntegerFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
