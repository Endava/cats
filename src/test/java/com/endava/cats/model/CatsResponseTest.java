package com.endava.cats.model;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CatsResponseTest {

    @Test
    void givenAStringJson_whenCreatingACatsResponseObject_thenTheStringIsParsedAsAJsonElement() {
        String body = "{'test': 'value'}";
        CatsResponse catsResponse = CatsResponse.from(200, body, "DELETE");

        Assertions.assertThat(catsResponse.getJsonBody()).isNotNull();
    }
}
