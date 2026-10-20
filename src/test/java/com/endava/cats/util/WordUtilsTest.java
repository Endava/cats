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
    @CsvSource(value = {
            "someURLValue,lower_snake_case,some_url_value",
            "someURLValue,kebab-case,some-url-value",
            "PascalCase,camelCase,pascalCase",
            "PascalCase,PascalCase,PascalCase",
            "MiXeD,lowercase,mixed",
            "someURLValue,unknown,SOME_URL_VALUE",
            "null,null,null",
            "name,null,name"
    }, nullValues = "null")
    void shouldConvertToDetectedCasing(String name, String casingConvention, String expected) {
        Assertions.assertThat(WordUtils.convertToDetectedCasing(name, casingConvention)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "properties,property",
            "entities,entity",
            "activities,activity",
            "parties,party",
            "cities,city",
            "studies,study",
            "copies,copy",
            "policies,policy",
            "categories,category",
            "movies,movie",
            "cookies,cookie",
            "purchases,purchase",
            "releases,release",
            "cases,case",
            "databases,database",
            "houses,house",
            "caches,cache",
            "matches,match",
            "sizes,size",
            "archives,archive",
            "objectives,objective",
            "knives,knife",
            "wolves,wolf",
            "leaves,leaf",
            "leaves,leave",
            "addresses,address",
            "statuses,status",
            "buses,bus",
            "boxes,box",
            "taxes,tax",
            "analyses,analysis",
            "aliases,alias",
            "canvases,canvas",
            "campuses,campus",
            "processes,process",
            "businesses,business",
            "quizzes,quiz",
            "children,child",
            "people,person",
            "criteria,criterion",
            "indices,index",
            "indexes,index",
            "axes,axis",
            "orders,order",
            "ids,id",
            "uuids,uuid",
            "bankaccounts,bankaccount",
            "heroes,hero",
            "potatoes,potato",
            "photos,photo",
            "hospitais,hospital",
            "animais,animal"
    })
    void shouldMatchSameNounForms(String first, String second) {
        Assertions.assertThat(WordUtils.sameNoun(first, second)).isTrue();
        Assertions.assertThat(WordUtils.sameNoun(second, first)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "objective,object",
            "organization,organ",
            "settings,set",
            "activity,active",
            "generation,general",
            "business,bus",
            "case,cache",
            "status,state",
            "policy,police",
            "product,production",
            "states,stats",
            "cares,car",
            "pines,pin",
            "mines,min",
            "sites,sit",
            "notes,not"
    })
    void shouldNotMatchDifferentNouns(String first, String second) {
        Assertions.assertThat(WordUtils.sameNoun(first, second)).isFalse();
        Assertions.assertThat(WordUtils.sameNoun(second, first)).isFalse();
    }

    @Test
    void shouldReturnNounForms() {
        Assertions.assertThat(WordUtils.nounForms(null)).isEmpty();
        Assertions.assertThat(WordUtils.nounForms("")).isEmpty();
        Assertions.assertThat(WordUtils.nounForms("address")).containsExactly("address");
        Assertions.assertThat(WordUtils.nounForms("properties")).contains("properties", "property");
    }
}
