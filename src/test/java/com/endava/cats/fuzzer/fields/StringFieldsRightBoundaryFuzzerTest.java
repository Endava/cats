package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

@QuarkusTest
class StringFieldsRightBoundaryFuzzerTest {
    private StringFieldsRightBoundaryFuzzer stringFieldsRightBoundaryFuzzer;

    @BeforeEach
    void setup() {
        stringFieldsRightBoundaryFuzzer = new StringFieldsRightBoundaryFuzzer(null, null, null, null);
    }

    @Test
    void givenANewStringFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFieldsRightBoundaryFuzzer() {
        StringSchema nrSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", nrSchema)).build();
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("string"))).isTrue();
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.getBoundaryValue(nrSchema)).isNotNull();
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.hasBoundaryDefined("test", data)).isFalse();
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.description()).isNotNull();

        nrSchema.setMaxLength(2);
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.hasBoundaryDefined("test", data)).isTrue();

    }

    @Test
    void shouldNotHaveBoundariesDefinedWhenMaxLengthIsIntegerMaxValue() {
        StringSchema nrSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", nrSchema)).build();
        nrSchema.setMaxLength(Integer.MAX_VALUE);
        Assertions.assertThat(stringFieldsRightBoundaryFuzzer.hasBoundaryDefined("test", data)).isFalse();
    }

}
