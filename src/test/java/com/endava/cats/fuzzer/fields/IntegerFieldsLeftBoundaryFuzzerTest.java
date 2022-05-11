package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class IntegerFieldsLeftBoundaryFuzzerTest {
    private IntegerFieldsLeftBoundaryFuzzer integerFieldsLeftBoundaryFuzzer;

    @BeforeEach
    void setup() {
        integerFieldsLeftBoundaryFuzzer = new IntegerFieldsLeftBoundaryFuzzer(null, null, null, null);
    }

    @Test
    void givenANewIntegerFieldsLeftBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheIntegerFieldsLeftBoundaryFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(integerFieldsLeftBoundaryFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(IntegerSchema.class))).isTrue();
        Assertions.assertThat(integerFieldsLeftBoundaryFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(integerFieldsLeftBoundaryFuzzer.description()).isNotNull();
    }
}
