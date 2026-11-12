package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class ResponseSimilarityTest {

    @Test
    void shouldReturnZeroWhenStatusCodesDiffer() {
        CatsResponse ok = jsonResponse(200, "{\"items\":[{\"id\":1}]}");
        CatsResponse notFound = jsonResponse(404, "{\"items\":[{\"id\":1}]}");

        double score = ResponseSimilarity.similarity(ok, notFound, inputs("alice", "alice' AND '1'='2", "alice"));
        Assertions.assertThat(score).isZero();
    }

    @Test
    void shouldReturnZeroWhenRedirectLocationsDiffer() {
        CatsResponse loginSuccess = CatsResponse.builder()
                .responseCode(302)
                .headers(List.of(new KeyValuePair<>("Location", "/dashboard")))
                .body("")
                .build();
        CatsResponse loginFailure = CatsResponse.builder()
                .responseCode(302)
                .headers(List.of(new KeyValuePair<>("Location", "/login?error=1")))
                .body("")
                .build();

        double score = ResponseSimilarity.similarity(loginSuccess, loginFailure, inputs("a", "b", "a"));
        Assertions.assertThat(score).isZero();
    }

    @Test
    void shouldTreatReorderedJsonKeysUnorderedArraysVolatileMetadataAndReflectedInputsAsIdentical() {
        CatsResponse baseline = jsonResponse(200, """
                {"query":"alice","timestamp":"2026-11-11T10:00:00Z","requestId":"req-1",
                 "results":[{"id":1,"name":"Alice"},{"id":2,"name":"Alicia"}]}
                """);
        CatsResponse neutralProbe = jsonResponse(200, """
                {"results":[{"name":"Alicia","id":2},{"name":"Alice","id":1}],
                 "requestId":"req-2","timestamp":"2026-11-11T10:00:05Z","query":"alice' AND '1'='1"}
                """);

        double score = ResponseSimilarity.similarity(baseline, neutralProbe,
                inputs("alice", "alice' AND '1'='1", "alice"));
        Assertions.assertThat(score).isEqualTo(1.0);
    }

    @Test
    void shouldScoreNonEmptyVsEmptyJsonCollectionBelowDifferentThreshold() {
        CatsResponse baseline = jsonResponse(200, """
                {"query":"alice","results":[{"id":1,"name":"Alice"},{"id":2,"name":"Alicia"}]}
                """);
        CatsResponse falseProbe = jsonResponse(200, """
                {"query":"alice' AND '1'='2","results":[]}
                """);

        double score = ResponseSimilarity.similarity(baseline, falseProbe,
                inputs("alice", "alice' AND '1'='2", "alice"));
        Assertions.assertThat(score).isLessThan(ResponseSimilarity.DIFFERENT_THRESHOLD);
    }

    @Test
    void shouldDistinguishPlainTextRowCountsAndBooleans() {
        CatsResponse zeroMatched = jsonResponse(200, "MATCHED: 0");
        CatsResponse fiveMatched = jsonResponse(200, "MATCHED: 5");

        double countDiffScore = ResponseSimilarity.similarity(zeroMatched, fiveMatched,
                inputs("foo", "foo' OR '1'='1", "foo"));
        double booleanDiffScore = ResponseSimilarity.similarity(
                jsonResponse(200, "false"),
                jsonResponse(200, "true"),
                inputs("foo", "foo' OR '1'='1", "foo"));

        Assertions.assertThat(countDiffScore).isLessThan(ResponseSimilarity.DIFFERENT_THRESHOLD);
        Assertions.assertThat(booleanDiffScore).isLessThan(ResponseSimilarity.DIFFERENT_THRESHOLD);
    }

    private static CatsResponse jsonResponse(int status, String body) {
        return CatsResponse.builder().responseCode(status).body(body).build();
    }

    private static ResponseSimilarity.ComparisonInputs inputs(String first, String second, String orig) {
        return new ResponseSimilarity.ComparisonInputs(first, second, orig);
    }
}
