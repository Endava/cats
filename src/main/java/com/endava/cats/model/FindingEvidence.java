package com.endava.cats.model;


import java.util.Optional;

/** Immutable oracle evidence shared by report classification and blackbox severity filtering. */
public record FindingEvidence(String fuzzer, String httpMethod, int responseCode, String reason,
                              FindingConfidence confidence, String responseBody, String resultDetails) {
    public static FindingEvidence from(CatsTestCaseSummary summary) {
        return new FindingEvidence(summary.getFuzzer(), summary.getHttpMethod(), summary.getHttpResponseCode(),
                summary.getResultReason(), summary.getConfidence(), summary.getResponseBody(), summary.getResultDetails());
    }

    public static FindingEvidence from(CatsTestCase test) {
        return from(test, test.getFuzzer());
    }

    public static FindingEvidence from(CatsTestCase test, String activeFuzzer) {
        CatsResponse response = Optional.ofNullable(test.getResponse()).orElse(CatsResponse.empty());
        String method = Optional.ofNullable(test.getRequest()).map(request -> request.getHttpMethod()).orElse("");
        return new FindingEvidence(activeFuzzer, method, response.getResponseCode(), test.getResultReason(),
                test.getConfidence(), response.getBody(), test.getResultDetails());
    }
}
