package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class CatsModeLUtilsTest {

    @ParameterizedTest
    @CsvSource(value = {
            "#/components/schemas/SomeSchema, SomeSchema",
            "#/components/schemas/AnotherSchema, AnotherSchema",
            "#/components/schemas/YetAnotherSchema, YetAnotherSchema",
            "null,null", "#/paths/path1,#/paths/path1"}, nullValues = "null")
    void shouldReturnSimpleReference(String ref, String expected) {
        String result = CatsModelUtils.getSimpleRef(ref);

        Assertions.assertThat(result).isEqualTo(expected);
    }
}
