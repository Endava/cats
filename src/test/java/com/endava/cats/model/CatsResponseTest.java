package com.endava.cats.model;

import com.google.common.collect.Sets;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;

@QuarkusTest
class CatsResponseTest {

    @Test
    void givenAStringJson_whenCreatingACatsResponseObject_thenTheStringIsParsedAsAJsonElement() {
        String body = "{'test': 'value'}";
        CatsResponse catsResponse = CatsResponse.from(200, body, "DELETE", 2);

        Assertions.assertThat(catsResponse.getJsonBody()).isNotNull();
    }

    @Test
    void shouldParseFuzzedField() {
        String body = "{'test': 'value'}";
        HashSet<String> fuzzedFields = Sets.newHashSet("test#subTest");
        CatsResponse catsResponse = CatsResponse.from(200, body, "DELETE", 2, Collections.singletonList(CatsHeader.builder().build()), fuzzedFields);

        Assertions.assertThat(catsResponse.getFuzzedField()).isEqualTo("subTest");
    }
}
