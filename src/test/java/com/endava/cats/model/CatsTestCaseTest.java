package com.endava.cats.model;


import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CatsTestCaseTest {

    @Test
    void shouldNotUpdateServer() {
        CatsTestCase catsTestCase = new CatsTestCase();
        catsTestCase.setFullRequestPath("http://localhost:8080/orders");
        catsTestCase.updateServer(null);

        Assertions.assertThat(catsTestCase.getFullRequestPath()).isEqualTo("http://localhost:8080/orders");
    }

    @Test
    void shouldUpdateServer() {
        CatsTestCase catsTestCase = new CatsTestCase();
        catsTestCase.setFullRequestPath("http://localhost:8080/orders");
        catsTestCase.setRequest(CatsRequest.builder().url("http://localhost:8080/orders").build());
        catsTestCase.setServer("http://localhost:8080");
        catsTestCase.updateServer("http://example.com");

        Assertions.assertThat(catsTestCase.getFullRequestPath()).isEqualTo("http://example.com/orders");
        Assertions.assertThat(catsTestCase.getRequest().getUrl()).isEqualTo("http://example.com/orders");
    }

}
