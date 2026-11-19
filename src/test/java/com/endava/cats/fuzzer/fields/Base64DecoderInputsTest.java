package com.endava.cats.fuzzer.fields;

import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

@QuarkusTest
class Base64DecoderInputsTest {
    @ParameterizedTest
    @CsvSource({"byte,,attachment,true", "binary,,attachment,false", ",base64,attachment,true",
            ",base64url,attachment,true", ",BASE64,attachment,true", ",gzip,attachment,false",
            ",,next_cursor,true", ",,pageToken,true", ",,token,false", ",,refreshToken,false", ",,description,false"})
    void shouldSelectOnlyDeclaredBase64StringsAndRecognizedCursors(String format, String encoding, String field, boolean expected) {
        Schema<String> schema = new Schema<String>().type("string").format(format).contentEncoding(encoding);

        Assertions.assertThat(Base64DecoderInputs.appliesTo(schema, field)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"integer", "number", "object", "array", "boolean"})
    void shouldSkipNonStringsEvenWithMatchingNamesAndEncoding(String type) {
        Assertions.assertThat(Base64DecoderInputs.appliesTo(new Schema<>().type(type).contentEncoding("base64"), "cursor")).isFalse();
    }

    @Test
    void shouldSkipMissingSchemasAndEnumFields() {
        Schema<String> schema = new Schema<String>().type("string").format("byte");
        schema.setEnum(List.of("e30="));

        Assertions.assertThat(Base64DecoderInputs.appliesTo(null, "cursor")).isFalse();
        Assertions.assertThat(Base64DecoderInputs.appliesTo(schema, "cursor")).isFalse();
    }

    @Test
    void shouldRecognizeOpenApi31StringTypes() {
        Schema<String> schema = new Schema<String>().types(Set.of("string")).contentEncoding("base64");

        Assertions.assertThat(Base64DecoderInputs.appliesTo(schema, "attachment")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {64, 128, 256})
    void shouldExerciseCompleteTruncatedAndIncorrectlyPaddedTailsAroundEachBoundary(int boundary) {
        String prefix = "A".repeat(boundary);
        String shortPrefix = "A".repeat(boundary - 1);

        Assertions.assertThat(Base64DecoderInputs.boundaryValues())
                .contains(shortPrefix, shortPrefix + "=", prefix, prefix + "=", prefix + "A", shortPrefix + "==");
    }

    @Test
    void shouldKeepTheMatrixBoundedDistinctAndSeparateFromLegacyCursorInputs() {
        Assertions.assertThat(Base64DecoderInputs.boundaryValues()).hasSize(18).doesNotHaveDuplicates()
                .doesNotContainAnyElementsOf(Base64DecoderInputs.cursorValues())
                .allSatisfy(value -> Assertions.assertThat(value.toString()).hasSizeBetween(63, 257));
        Assertions.assertThat(Base64DecoderInputs.cursorValues())
                .containsExactly("e30=", "bnVsbA==", "bm90LWEtdGltZXN0YW1wOm5vdC1hbi1pZA==", "===invalid-cursor===");
    }

    @Test
    void shouldKeepSharedValuesImmutable() {
        List<Object> values = Base64DecoderInputs.boundaryValues();

        Assertions.assertThatThrownBy(() -> values.add("extra")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldPreserveEveryBoundaryValueWhenReferenceDataIsMerged() {
        Assertions.assertThat(Base64DecoderInputs.boundaryValues()).isNotEmpty().allSatisfy(value ->
                Assertions.assertThat(FuzzingStrategy.mergeFuzzing(value, "configured reference value")).isEqualTo(value));
    }
}
