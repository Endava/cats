package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@QuarkusTest
class CatsModelUtilsTest {

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

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideSchemasForEmptyObjectCheck")
    void isEmptyObjectSchema_shouldMatchExpected(String description,
                                                 Schema<?> schema,
                                                 boolean expected) {
        boolean actual = CatsModelUtils.isEmptyObjectSchema(schema);
        assertThat(actual)
                .as(description)
                .isEqualTo(expected);
    }

    private static List<Object[]> provideSchemasForEmptyObjectCheck() {
        return List.of(
                new Object[]{
                        "Type='object', no props, no ref, no composition",
                        new Schema<>().type("object"),
                        true
                },
                new Object[]{
                        "Type='OBJECT' (case‐insensitive)",
                        new Schema<>().type("OBJECT"),
                        true
                },
                new Object[]{
                        "Type=null, empty properties map",
                        new Schema<>().properties(new LinkedHashMap<>()),
                        true
                },
                new Object[]{
                        "Type=null, non‐empty properties map",
                        new Schema<>().properties(Map.of("x", new Schema<>())),
                        false
                },
                new Object[]{
                        "Object with $ref set",
                        new Schema<>().type("object").$ref("#/ref"),
                        false
                },
                new Object[]{
                        "Object with allOf non‐empty",
                        new Schema<>().type("object").allOf(List.of(new Schema<>())),
                        false
                },
                new Object[]{
                        "Object with anyOf non‐empty",
                        new Schema<>().type("object").anyOf(List.of(new Schema<>())),
                        false
                },
                new Object[]{
                        "Object with oneOf non‐empty",
                        new Schema<>().type("object").oneOf(List.of(new Schema<>())),
                        false
                },
                new Object[]{
                        "Type≠object, no other markers",
                        new Schema<>().type("string"),
                        false
                }
        );
    }
}
