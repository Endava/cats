package com.endava.cats.model;

import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class CatsRequestTest {

    @Test
    void shouldExpandMaskedPlaceholdersFromTheEnvironmentInCurlCommands() {
        CatsRequest request = CatsRequest.builder()
                .httpMethod("GET")
                .url("https://api.example.com/items?token=$$TOKEN")
                .headers(List.of(new KeyValuePair<>("Authorization", "$$Authorization"), new KeyValuePair<>("X-Note", "it's $5")))
                .build();

        Assertions.assertThat(request.toCurlCommand()).isEqualTo(
                "curl -i -X GET -H 'Authorization: '\"${Authorization}\"'' -H 'X-Note: it'\\''s $5' 'https://api.example.com/items?token='\"${TOKEN}\"''");
    }

    @Test
    void shouldReturnEmptyCurlForPlaceholderRequests() {
        Assertions.assertThat(CatsRequest.empty().toCurlCommand()).isEmpty();
    }
}
