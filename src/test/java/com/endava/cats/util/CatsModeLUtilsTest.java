package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
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

    @ParameterizedTest
    @CsvSource(value = {"email;[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,};true",
            "email;[A-Z]+;false",
            "uri;^(?:[a-z]+:)?//[^\\s]*;true",
            "wrong;^(?:[a-z]+:)?//[^\\\\s]*;false",
            "url;null;false",
            "password;[a-zA-Z0-9._%+\\-\\!#\\?]+;true",
            "wrong;[a-zA-Z0-9._%+\\-\\!#\\?]+;false",
            "password;[a-zA-Z0-9._%+\\-\\!#]+;false",
            "wrong;wrong;false"},
            delimiter = ';', nullValues = "null")
    void shouldTestEmailAndUrlNameMatches(String name, String pattern, boolean expected) {
        Schema<?> schema = new Schema<>();
        schema.addExtension(CatsModelUtils.X_CATS_FIELD_NAME, name);
        schema.setPattern(pattern);
        boolean result = CatsModelUtils.isComplexRegex(schema);

        Assertions.assertThat(result).isEqualTo(expected);
    }


}
