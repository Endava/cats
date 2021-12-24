package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

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
}
