package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@QuarkusTest
class CatsModelUtilsTest {

    @Test
    void shouldIdentifyFreeFormSchemas() {
        Schema<?> emptyWithMetadata = new Schema<>().description("any JSON value");
        emptyWithMetadata.addExtension(CatsModelUtils.X_CATS_FIELD_NAME, "items");

        Assertions.assertThat(CatsModelUtils.isFreeFormSchema(new Schema<>())).isTrue();
        Assertions.assertThat(CatsModelUtils.isFreeFormSchema(emptyWithMetadata)).isTrue();
        Assertions.assertThat(CatsModelUtils.isFreeFormSchema(new Schema<>().booleanSchemaValue(true))).isTrue();
        Assertions.assertThat(CatsModelUtils.isFreeFormSchema(new Schema<>().booleanSchemaValue(false))).isFalse();
        Assertions.assertThat(CatsModelUtils.isFreeFormSchema(new StringSchema())).isFalse();
        Assertions.assertThat(CatsModelUtils.isFreeFormSchema(new Schema<>()._enum(List.of("only")))).isFalse();
        Assertions.assertThat(CatsModelUtils.isFreeFormSchema(new Schema<>().pattern("[a-z]+"))).isFalse();
        Assertions.assertThat(CatsModelUtils.isFreeFormSchema(new Schema<>().maxLength(10))).isFalse();
        Assertions.assertThat(CatsModelUtils.isFreeFormSchema(new Schema<>().minContains(1))).isFalse();
        Assertions.assertThat(CatsModelUtils.isFreeFormSchema(new Schema<>().additionalProperties(false))).isFalse();
        Assertions.assertThat(CatsModelUtils.isFreeFormSchema(null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "date;2024-02-29;true", "date;2024-13-45;false",
            "date-time;2024-02-29T10:15:30Z;true", "date-time;2024-02-29T10:15:30+0000;false", "date-time;Current datetime;false",
            "uuid;4f553d2e-e328-420f-9458-bd7120fce0d9;true", "uuid;4f553d2e-e328-420f-9458-bd7120fce0d9 ;false",
            "email;not-validated;true", "null;anything;true"}, delimiter = ';', nullValues = "null", ignoreLeadingAndTrailingWhitespace = false)
    void shouldCheckValueMatchesWellKnownFormat(String format, String value, boolean expected) {
        Schema<?> schema = new StringSchema().format(format);

        Assertions.assertThat(CatsModelUtils.isValueMatchingFormat(schema, value)).isEqualTo(expected);
    }

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
                },
                new Object[]{
                        "Object with additionalProperties schema (map)",
                        new Schema<>().type("object").additionalProperties(new StringSchema()),
                        false
                },
                new Object[]{
                        "Object with additionalProperties=true",
                        new Schema<>().type("object").additionalProperties(true),
                        false
                },
                new Object[]{
                        "Object with additionalProperties=false and no properties",
                        new Schema<>().type("object").additionalProperties(false),
                        true
                }
        );
    }

    @Test
    void shouldIgnoreSyntheticCatsGetSchemaNames() {
        Assertions.assertThat(CatsModelUtils.isNotGeneratedPattern("CatsGetSchemaAbCdE")).isFalse();
        Assertions.assertThat(CatsModelUtils.isNotGeneratedPattern("UserResponse")).isTrue();
    }

    @Test
    void shouldCopyOpenApi31JsonSchemaAndOpenApi30DateTimeSchemaWithoutFailing() {
        Assertions.assertThat(CatsModelUtils.copySchema(null)).isNotNull();

        JsonSchema itemsSchema = new JsonSchema();
        itemsSchema.setSpecVersion(SpecVersion.V31);
        itemsSchema.setTypes(Set.of("string"));
        itemsSchema.setEnum(List.of("CAT", "DOG"));

        JsonSchema arraySchema = new JsonSchema();
        arraySchema.setSpecVersion(SpecVersion.V31);
        arraySchema.setTypes(Set.of("array"));
        arraySchema.setItems(itemsSchema);

        Schema<?> copied31 = CatsModelUtils.copySchema(arraySchema);
        Assertions.assertThat(CatsModelUtils.isArraySchema(copied31)).isTrue();
        Assertions.assertThat(CatsModelUtils.isStringSchema(copied31.getItems())).isTrue();
        Assertions.assertThat(CatsModelUtils.isObjectSchema(copied31.getItems())).isFalse();

        DateTimeSchema dateTime30 = new DateTimeSchema();
        dateTime30.setSpecVersion(SpecVersion.V30);
        dateTime30.setTypes(Set.of("string"));
        Schema<?> copied30 = CatsModelUtils.copySchema(dateTime30);
        Assertions.assertThat(CatsModelUtils.isDateTimeSchema(copied30)).isTrue();
    }

    @Test
    void shouldFindAllOfParentsAndChildrenAndFilterPolymorphicBaseSchemas() {
        Schema<?> baseSchema = new Schema<>().type("object");
        Schema<?> dogSchema = new Schema<>().allOf(List.of(new Schema<>().$ref("#/components/schemas/Pet")));
        Schema<?> catSchema = new Schema<>().allOf(List.of(new Schema<>().$ref("#/components/schemas/Pet")));
        Map<String, Schema> schemaMap = Map.of(
                "Pet", baseSchema,
                "Dog", dogSchema,
                "Cat", catSchema);

        Assertions.assertThat(CatsModelUtils.findAllOfParents(dogSchema)).containsExactly("Pet");
        Assertions.assertThat(CatsModelUtils.findAllOfChildrenByParent(schemaMap))
                .containsEntry("Pet", Set.of("Dog", "Cat"));

        List<Schema> oneOfSchemas = List.of(
                new Schema<>().$ref("#/components/schemas/Pet"),
                new Schema<>().$ref("#/components/schemas/Dog"),
                new Schema<>().$ref("#/components/schemas/Cat"));
        List<Schema> filtered = CatsModelUtils.filterPolymorphicBaseSchemas(
                oneOfSchemas, ref -> schemaMap.get(CatsModelUtils.getSimpleRef(ref)));

        Assertions.assertThat(filtered)
                .extracting(Schema::get$ref)
                .containsExactly("#/components/schemas/Dog", "#/components/schemas/Cat");
    }
}
