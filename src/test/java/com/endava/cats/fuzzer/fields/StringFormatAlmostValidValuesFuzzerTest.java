package com.endava.cats.fuzzer.fields;

import com.endava.cats.generator.format.api.InvalidDataFormat;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

@QuarkusTest
class StringFormatAlmostValidValuesFuzzerTest {
    private StringFormatAlmostValidValuesFuzzer stringFormatAlmostValidValuesFuzzer;

    @Inject
    InvalidDataFormat invalidDataFormat;

    @BeforeEach
    void setup() {
        stringFormatAlmostValidValuesFuzzer = new StringFormatAlmostValidValuesFuzzer(null, null, null, null, invalidDataFormat);
    }

    @Test
    void givenANewStringFormatAlmostValidValuesFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFormatAlmostValidValuesFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        nrSchema.setFormat("email");
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.getSchemaTypesTheFuzzerWillApplyTo()).containsExactly("string");
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.getBoundaryValue(nrSchema)).isNotNull();
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.description()).isNotNull();
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.typeOfDataSentToTheService()).isNotNull();

    }
}
