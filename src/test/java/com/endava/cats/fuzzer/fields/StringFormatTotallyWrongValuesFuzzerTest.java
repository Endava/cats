package com.endava.cats.fuzzer.fields;

import com.endava.cats.generator.format.api.InvalidDataFormat;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

@QuarkusTest
class StringFormatTotallyWrongValuesFuzzerTest {
    private StringFormatTotallyWrongValuesFuzzer stringFormatTotallyWrongValuesFuzzer;
    @Inject
    InvalidDataFormat invalidDataFormat;

    @BeforeEach
    void setup() {
        stringFormatTotallyWrongValuesFuzzer = new StringFormatTotallyWrongValuesFuzzer(null, null, null, null, invalidDataFormat);
    }

    @Test
    void givenANewStringFormatTotallyWrongValuesFuzzerTest_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFormatTotallyWrongValuesFuzzerTest() {
        NumberSchema nrSchema = new NumberSchema();
        nrSchema.setFormat("email");
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.getSchemaTypesTheFuzzerWillApplyTo()).containsExactly("string");
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.getBoundaryValue(nrSchema)).isNotNull();
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.description()).isNotNull();
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.typeOfDataSentToTheService()).isNotNull();

    }
}
