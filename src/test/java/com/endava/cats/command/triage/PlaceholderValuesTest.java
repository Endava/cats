package com.endava.cats.command.triage;

import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class PlaceholderValuesTest {
    private final TriageTestCase testCase = TriageFixtures.rejected("Fuzzer", "2XX", "/items", "{}", 400, "invalid");

    @ParameterizedTest
    @MethodSource("schemas")
    void shouldPreferTypedSchemaValues(Schema<?> schema, Object expected) {
        RequestSchemaLookup lookup = Mockito.mock(RequestSchemaLookup.class);
        Mockito.when(lookup.property("/items", "POST", "value")).thenReturn(Optional.of(schema));
        Mockito.when(lookup.resolve(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(new PlaceholderValues(lookup).forRequirement(testCase, "value", "boolean", Optional.empty())).isEqualTo(expected);
    }

    static Stream<Arguments> schemas() {
        return Stream.of(
                Arguments.of(new StringSchema()._enum(Arrays.asList(null, "valid")), "valid"),
                Arguments.of(new BooleanSchema(), true),
                Arguments.of(new IntegerSchema().minimum(BigDecimal.TEN), BigInteger.TEN),
                Arguments.of(new IntegerSchema().minimum(BigDecimal.TEN).exclusiveMinimum(true), BigInteger.valueOf(11)),
                Arguments.of(new NumberSchema().exclusiveMinimumValue(BigDecimal.TEN).maximum(new BigDecimal("10.5")), new BigDecimal("10.5")),
                Arguments.of(new NumberSchema(), BigDecimal.ONE),
                Arguments.of(new ArraySchema().items(new BooleanSchema()), List.of(true)),
                Arguments.of(new ArraySchema(), List.of(PlaceholderValues.DEFAULT_STRING)),
                Arguments.of(new StringSchema().format("EMAIL"), "#(email)"),
                Arguments.of(new StringSchema().format("uuid"), "#(uuid)"),
                Arguments.of(new StringSchema().format("date"), "#(today)"),
                Arguments.of(new StringSchema().format("date-time"), "#(now)"),
                Arguments.of(new StringSchema().minLength(12).maxLength(20), "#(alphanumeric(12))"),
                Arguments.of(new StringSchema().maxLength(3), "#(alphanumeric(3))"),
                Arguments.of(new StringSchema(), PlaceholderValues.DEFAULT_STRING),
                Arguments.of(new StringSchema()._enum(List.of()), PlaceholderValues.DEFAULT_STRING));
    }

    @ParameterizedTest
    @CsvSource(value = {"numeric|null|1", "boolean|null|true", "invalid|1|1", "invalid|false|true", "invalid|{}|{}", "invalid|null|#(alphanumeric(8))", "invalid|\"text\"|#(alphanumeric(8))", "array|[]|[#(alphanumeric(8))]", "list|[2]|[2]", "invalid|[2]|[2]", "array|1|[#(alphanumeric(8))]"}, delimiter = '|')
    void shouldFallBackToMessageThenCurrentValue(String message, String json, String expected) {
        Object value = new PlaceholderValues(RequestSchemaLookup.none()).forRequirement(testCase, "value", message,
                Optional.of(JsonUtils.parseAsJsonElement(json)));
        assertThat(value.toString()).isEqualTo(expected);
    }

    @Test
    void shouldFallBackFromUnknownAndNullOnlyEnumSchemas() {
        RequestSchemaLookup lookup = Mockito.mock(RequestSchemaLookup.class);
        Mockito.when(lookup.property("/items", "POST", "value")).thenReturn(Optional.of(new Schema<>()));
        Mockito.when(lookup.properties(Mockito.any())).thenReturn(Map.of("child", new StringSchema()));
        PlaceholderValues values = new PlaceholderValues(lookup);
        assertThat(values.forRequirement(testCase, "value", "", Optional.empty())).isEqualTo(Map.of());
        Mockito.when(lookup.properties(Mockito.any())).thenReturn(Map.of());
        assertThat(values.forRequirement(testCase, "value", "", Optional.empty())).isEqualTo(PlaceholderValues.DEFAULT_STRING);
        Mockito.when(lookup.property("/items", "POST", "value")).thenReturn(Optional.of(new StringSchema()._enum(Collections.singletonList(null))));
        assertThat(values.forRequirement(testCase, "value", "numeric", Optional.empty())).isEqualTo(1);
    }

    @Test
    void shouldGenerateUniqueStringsOnly() {
        RequestSchemaLookup lookup = Mockito.mock(RequestSchemaLookup.class);
        PlaceholderValues values = new PlaceholderValues(lookup);
        assertThat(values.forUniqueness(testCase, "unknown")).contains(PlaceholderValues.DEFAULT_STRING);
        Mockito.when(lookup.property("/items", "POST", "value")).thenReturn(Optional.of(new IntegerSchema()));
        assertThat(values.forUniqueness(testCase, "value")).isEmpty();
        Mockito.when(lookup.property("/items", "POST", "value")).thenReturn(Optional.of(new StringSchema().maxLength(4)));
        assertThat(values.forUniqueness(testCase, "value")).contains("#(alphanumeric(4))");
    }
}
