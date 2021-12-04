package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.EmailSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.PasswordSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class StringFormatTotallyWrongValuesFuzzerTest {
    private StringFormatTotallyWrongValuesFuzzer stringFormatTotallyWrongValuesFuzzer;

    @BeforeEach
    void setup() {
        stringFormatTotallyWrongValuesFuzzer = new StringFormatTotallyWrongValuesFuzzer(null, null, null, null);
    }

    @Test
    void givenANewStringFormatTotallyWrongValuesFuzzerTest_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFormatTotallyWrongValuesFuzzerTest() {
        NumberSchema nrSchema = new NumberSchema();
        nrSchema.setFormat("email");
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.getSchemasThatTheFuzzerWillApplyTo()).containsExactly(StringSchema.class, DateSchema.class, DateTimeSchema.class, PasswordSchema.class, UUIDSchema.class, EmailSchema.class);
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.getBoundaryValue(nrSchema)).isNotNull();
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.description()).isNotNull();
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.typeOfDataSentToTheService()).isNotNull();

    }
}
