package com.endava.cats.model;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class RequestVocabularyTest {

    @Test
    void shouldCollectNestedFieldNamesAndIdentifyingValuesFromPayloadAndQuery() {
        RequestVocabulary vocabulary = RequestVocabulary.from(
                "{\"name\":\"John Doe\",\"address\":{\"city\":\"Paris\"},\"tags\":[\"premium\"],\"active\":true,\"age\":7}",
                "http://localhost:8080/pets?status=sold%20out&q=ab", "/pets");

        Assertions.assertThat(vocabulary.fieldNames()).containsExactlyInAnyOrder("name", "address", "city", "tags", "active", "age", "status", "q");
        Assertions.assertThat(vocabulary.values()).containsExactlyInAnyOrder("John Doe", "Paris", "premium", "sold out");
    }

    @Test
    void shouldCollectFieldNamesAndValuesFromFormPayloads() {
        RequestVocabulary vocabulary = RequestVocabulary.from("motherTongue=%27+OR+%271%27%3D%271&language=uk-UA&x=", null, null);

        Assertions.assertThat(vocabulary.fieldNames()).containsExactlyInAnyOrder("motherTongue", "language", "x");
        Assertions.assertThat(vocabulary.values()).containsExactlyInAnyOrder("' OR '1'='1", "uk-UA");
    }

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8080/api/v1/pets/0xa14f5/toys, /pets/{id}/toys, 0xa14f5",
            "/pets/abc%20def, /pets/{id}, abc def",
            "https://host/pets/owner-1/visits/visit-2?x=1, /pets/{owner}/visits/{visit}, owner-1|visit-2"
    })
    void shouldAlignPathParametersWithTheEndOfTheUrl(String url, String contractPath, String expected) {
        RequestVocabulary vocabulary = RequestVocabulary.from(null, url, contractPath);

        Assertions.assertThat(vocabulary.values()).containsExactlyInAnyOrder(expected.split("\\|"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "not json | /pets | /pets",
            "{\"a\":\"xy\"} | /pets/1 | /pets/{id}",
            "{\"a\":\"false\"} | /a/b | /a/b/{c}"
    }, delimiter = '|')
    void shouldIgnoreNonJsonPayloadsAndNonIdentifyingValues(String payload, String url, String contractPath) {
        Assertions.assertThat(RequestVocabulary.from(payload, url, contractPath).values()).isEmpty();
    }
}
