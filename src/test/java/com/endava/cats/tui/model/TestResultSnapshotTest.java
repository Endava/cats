package com.endava.cats.tui.model;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class TestResultSnapshotTest {
    @Test
    void givenMaskedHeaders_whenCreatingSnapshot_thenRequestAndResponseValuesAreMasked() {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setTestId("Test 1");
        testCase.setRequest(CatsRequest.builder()
                .httpMethod("GET")
                .headers(List.of(new KeyValuePair<>("API-Token", "secret")))
                .build());
        testCase.setResponse(CatsResponse.builder()
                .responseCode(200)
                .headers(List.of(new KeyValuePair<>("API-Token", "response-secret")))
                .build());

        TestResultSnapshot snapshot = TestResultSnapshot.from(testCase, Set.of("API-Token"));

        assertThat(snapshot.request().headers()).containsExactly(
                new TestResultSnapshot.HeaderSnapshot("API-Token", "$$APIToken"));
        assertThat(snapshot.response().headers()).containsExactly(
                new TestResultSnapshot.HeaderSnapshot("API-Token", "$$APIToken"));
    }
}
