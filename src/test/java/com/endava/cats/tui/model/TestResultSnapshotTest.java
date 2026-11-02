package com.endava.cats.tui.model;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.SensitiveDataPolicy;
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

    @Test
    void shouldPreserveRedactedHeadersAndFormatTargetsAndCorrelations() {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setTestId("Test 2");
        testCase.setRequest(CatsRequest.builder()
                .httpMethod("POST")
                .headers(List.of(
                        new KeyValuePair<>("Authorization", SensitiveDataPolicy.REDACTED),
                        new KeyValuePair<>("X-Plain", "visible")))
                .build());
        testCase.getRequestProvenance().addMutationTargets(List.of(RequestTarget.body("name")));
        testCase.getRequestProvenance().addRuntimeCorrelation(
                ResourceCorrelation.builder()
                        .sourceMethod("POST")
                        .sourcePath("/users")
                        .sourceLocation("response.body.$.id")
                        .target(RequestTarget.path("userId"))
                        .value("123")
                        .build());

        TestResultSnapshot snapshot = TestResultSnapshot.from(testCase, Set.of("Authorization"));
        assertThat(snapshot.request().headers()).containsExactly(
                new TestResultSnapshot.HeaderSnapshot("Authorization", SensitiveDataPolicy.REDACTED),
                new TestResultSnapshot.HeaderSnapshot("X-Plain", "visible"));
        assertThat(snapshot.mutationTargets()).containsExactly("Body field: name");
        assertThat(snapshot.resourceCorrelations()).containsExactly("path:userId <- POST /users (response.body.$.id)");
    }
}
