package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.ByteArraySchema;
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
class StringFormatAlmostValidValuesFuzzerTest {
    private StringFormatAlmostValidValuesFuzzer stringFormatAlmostValidValuesFuzzer;

    @BeforeEach
    void setup() {
        stringFormatAlmostValidValuesFuzzer = new StringFormatAlmostValidValuesFuzzer(null, null, null, null);
    }

    @Test
    void givenANewStringFormatAlmostValidValuesFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFormatAlmostValidValuesFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        nrSchema.setFormat("email");
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.getSchemasThatTheFuzzerWillApplyTo()).containsExactly(StringSchema.class, DateSchema.class, DateTimeSchema.class, PasswordSchema.class, UUIDSchema.class, EmailSchema.class, ByteArraySchema.class);
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.getBoundaryValue(nrSchema)).isNotNull();
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.description()).isNotNull();
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.typeOfDataSentToTheService()).isNotNull();

    }
}
