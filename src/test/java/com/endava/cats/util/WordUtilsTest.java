package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

@QuarkusTest
class WordUtilsTest {

    @Test
    void shouldReturnAllCombinations() {
        String[] words = new String[]{"pet", "name", "id"};
        Set<String> result = WordUtils.createWordCombinations(words);

        Assertions.assertThat(result).containsOnly("ID", "Id", "NAME-ID", "NAMEID", "NAME_ID", "Name-Id", "NameId",
                "Name_Id", "PET-NAME-ID", "PETNAMEID", "PET_NAME_ID", "Pet-Name-Id", "PetNameId", "Pet_Name_Id",
                "id", "name-Id", "name-id", "nameId", "name_Id", "name_id", "nameid", "pet-Name-Id", "pet-name-id", "petNameId", "pet_Name_Id", "pet_name_id", "petnameid");
    }

    @ParameterizedTest
    @CsvSource({
            "UPPER_SNAKE,UPPER_SNAKE_CASE",
            "lower_snake,lower_snake_case",
            "kebab-case,kebab-case",
            "camelCase,camelCase",
            "PascalCase,PascalCase",
            "lowercase,lowercase",
            "UPPERCASE,UPPER_SNAKE_CASE"
    })
    void shouldDetectCasingFromString(String sample, String expected) {
        Assertions.assertThat(WordUtils.detectCasingFromString(sample)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "someURLValue,lower_snake_case,some_url_value",
            "someURLValue,kebab-case,some-url-value",
            "PascalCase,camelCase,pascalCase",
            "PascalCase,PascalCase,PascalCase",
            "MiXeD,lowercase,mixed",
            "someURLValue,unknown,SOME_URL_VALUE"
    })
    void shouldConvertToDetectedCasing(String name, String casingConvention, String expected) {
        Assertions.assertThat(WordUtils.convertToDetectedCasing(name, casingConvention)).isEqualTo(expected);
    }
}
