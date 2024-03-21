package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

@QuarkusTest
class StringFieldsLeftBoundaryFuzzerTest {
    private StringFieldsLeftBoundaryFuzzer stringFieldsLeftBoundaryFuzzer;

    @BeforeEach
    void setup() {
        stringFieldsLeftBoundaryFuzzer = new StringFieldsLeftBoundaryFuzzer(null, null, null);
    }

    @Test
    void givenANewStringFieldsLeftBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFieldsLeftBoundaryFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", nrSchema)).build();
        Assertions.assertThat(stringFieldsLeftBoundaryFuzzer.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("string"))).isTrue();
        Assertions.assertThat(stringFieldsLeftBoundaryFuzzer.getBoundaryValue(nrSchema)).isNotNull();
        Assertions.assertThat(stringFieldsLeftBoundaryFuzzer.hasBoundaryDefined("test", data)).isTrue();
        Assertions.assertThat(stringFieldsLeftBoundaryFuzzer.description()).isNotNull();

        nrSchema.setMinLength(2);
        Assertions.assertThat(stringFieldsLeftBoundaryFuzzer.hasBoundaryDefined("test", data)).isTrue();

    }

    @Test
    void shouldHaveBoundaryDefined() {
        StringSchema nrSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", nrSchema)).build();
        Assertions.assertThat(stringFieldsLeftBoundaryFuzzer.hasBoundaryDefined("test", data)).isTrue();
    }

}
