package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class IntegerFieldsRightBoundaryFuzzerTest {
    private IntegerFieldsRightBoundaryFuzzer integerFieldsRightBoundaryFuzzer;

    @BeforeEach
    void setup() {
        integerFieldsRightBoundaryFuzzer = new IntegerFieldsRightBoundaryFuzzer(null, null, null, null);
    }

    @Test
    void givenANewIntegerFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheIntegerFieldsRightBoundaryFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(integerFieldsRightBoundaryFuzzer.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("integer"))).isTrue();
        Assertions.assertThat(integerFieldsRightBoundaryFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(integerFieldsRightBoundaryFuzzer.description()).isNotNull();
        Assertions.assertThat(integerFieldsRightBoundaryFuzzer.getBoundaryValue(nrSchema)).isInstanceOf(Long.class);
    }
}
