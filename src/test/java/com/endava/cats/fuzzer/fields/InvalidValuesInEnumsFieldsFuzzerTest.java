package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

@QuarkusTest
class InvalidValuesInEnumsFieldsFuzzerTest {

    private InvalidValuesInEnumsFieldsFuzzer invalidValuesInEnumsFieldsFuzzer;
    private ProcessingArguments processingArguments;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        invalidValuesInEnumsFieldsFuzzer = new InvalidValuesInEnumsFieldsFuzzer(null, null, null, processingArguments);
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
    void shouldHandleEmptyAndNullOnlyEnumListsGracefully() {
        StringSchema emptyEnum = new StringSchema();
        emptyEnum.setEnum(Collections.emptyList());
        FuzzingData emptyData = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("status", emptyEnum)).build();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getBoundaryValue(emptyEnum)).isNull();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.hasBoundaryDefined("status", emptyData)).isFalse();

        StringSchema nullOnlyEnum = new StringSchema();
        nullOnlyEnum.setEnum(Collections.singletonList(null));
        FuzzingData nullData = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("status", nullOnlyEnum)).build();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getBoundaryValue(nullOnlyEnum)).isNull();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.hasBoundaryDefined("status", nullData)).isFalse();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getBoundaryValue(null)).isNull();
    }

    @Test
    void shouldNeverGenerateValidSingleLetterEnumValue() {
        List<String> threeDsStatuses = List.of("Y", "N", "U", "A", "C", "D", "R", "I");
        StringSchema schema = new StringSchema();
        schema.setEnum(threeDsStatuses);

        for (int i = 0; i < 100; i++) {
            String candidate = invalidValuesInEnumsFieldsFuzzer.getBoundaryValue(schema);
            Assertions.assertThat(candidate).hasSize(1);
            Assertions.assertThat(threeDsStatuses)
                    .noneMatch(valid -> valid.equalsIgnoreCase(candidate));
        }
    }

    @Test
    void shouldFallbackCleanlyWhenAllLettersAreInEnum() {
        List<String> allLetters = IntStream.rangeClosed('A', 'Z')
                .mapToObj(c -> String.valueOf((char) c))
                .toList();
        StringSchema lettersSchema = new StringSchema();
        lettersSchema.setEnum(allLetters);
        String digitCandidate = invalidValuesInEnumsFieldsFuzzer.getBoundaryValue(lettersSchema);
        Assertions.assertThat(digitCandidate).matches("^[0-9]$");

        List<String> allAlphaNum = new java.util.ArrayList<>(allLetters);
        IntStream.rangeClosed('0', '9').mapToObj(c -> String.valueOf((char) c)).forEach(allAlphaNum::add);
        allAlphaNum.add("CATS_INVALID_ENUM");
        StringSchema exhaustedSchema = new StringSchema();
        exhaustedSchema.setEnum(allAlphaNum);
        String fallback = invalidValuesInEnumsFieldsFuzzer.getBoundaryValue(exhaustedSchema);
        Assertions.assertThat(fallback).isEqualTo("CATS_INVALID_ENUM_1");
        Assertions.assertThat(allAlphaNum)
                .noneMatch(valid -> valid.toLowerCase(Locale.ROOT).equals(fallback.toLowerCase(Locale.ROOT)));
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

    @Test
    void shouldReturn4XXIfNotAllowInvalidEnumValues() {
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
    }

    @Test
    void shouldReturn2XXIfAllowInvalidEnumValues() {
        Mockito.when(processingArguments.isAllowInvalidEnumValues()).thenReturn(true);
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
    }
}
