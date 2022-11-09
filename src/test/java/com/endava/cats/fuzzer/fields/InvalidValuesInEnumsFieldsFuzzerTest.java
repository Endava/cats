package com.endava.cats.fuzzer.fields;

import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

@QuarkusTest
class InvalidValuesInEnumsFieldsFuzzerTest {

    private InvalidValuesInEnumsFieldsFuzzer invalidValuesInEnumsFieldsFuzzer;

    @BeforeEach
    void setup() {
        invalidValuesInEnumsFieldsFuzzer = new InvalidValuesInEnumsFieldsFuzzer(null, null, null, null);
    }

    @Test
    void shouldNotHaveBoundaryDefined() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("string"))).isTrue();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getBoundaryValue(stringSchema)).isNullOrEmpty();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.hasBoundaryDefined("test", data)).isFalse();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldHaveBoundaryDefined() {
        StringSchema stringSchema = new StringSchema();
        stringSchema.setEnum(Collections.singletonList("TEST"));
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getSchemaTypesTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.equalsIgnoreCase("string"))).isTrue();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getBoundaryValue(stringSchema)).hasSizeLessThanOrEqualTo(4);
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.hasBoundaryDefined("test", data)).isTrue();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
