package com.endava.cats.model;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class CatsResponseTest {

    @Test
    void givenAStringJson_whenCreatingACatsResponseObject_thenTheStringIsParsedAsAJsonElement() {
        String body = "{'test': 'value'}";
        CatsResponse catsResponse = CatsResponse.from(200, body, "DELETE", 2);

        Assertions.assertThat(catsResponse.getJsonBody()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({"100,0,false", "100,1000,false", "100,100,false", "1000,100,true"})
    void shouldTestResponseTimeExceeding(int responseTime, int maxResponseTime, boolean expected) {
        CatsResponse response = CatsResponse.builder().responseTimeInMs(responseTime).build();
        boolean actual = response.exceedsExpectedResponseTime(maxResponseTime);

        Assertions.assertThat(actual).isEqualTo(expected);
    }
}
