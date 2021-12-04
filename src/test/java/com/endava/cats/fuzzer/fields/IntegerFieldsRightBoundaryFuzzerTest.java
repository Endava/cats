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
class IntegerFieldsRightBoundaryFuzzerTest {
    private IntegerFieldsRightBoundaryFuzzer integerFieldsRightBoundaryFuzzer;

    @BeforeEach
    void setup() {
        integerFieldsRightBoundaryFuzzer = new IntegerFieldsRightBoundaryFuzzer(null, null, null, null);
    }

    @Test
    void givenANewIntegerFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheIntegerFieldsRightBoundaryFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(integerFieldsRightBoundaryFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(IntegerSchema.class))).isTrue();
        Assertions.assertThat(NumberUtils.isCreatable(integerFieldsRightBoundaryFuzzer.getBoundaryValue(nrSchema))).isTrue();
        Assertions.assertThat(integerFieldsRightBoundaryFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(integerFieldsRightBoundaryFuzzer.description()).isNotNull();
    }
}
