package com.endava.cats.command.triage;

import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.JsonUtils;

import java.util.Arrays;

/**
 * Builds {@link TriageTestCase} instances from compact report-style JSON for triage unit tests.
 */
final class TriageFixtures {

    private TriageFixtures() {
    }

    static TriageTestCase testCase(int number, String json) {
        return new TriageTestCase(number, JsonUtils.GSON.fromJson(json, CatsTestCase.class));
    }

    /**
     * A POST {@code path} test with the given fuzzer, expected result, payload, status and response message.
     */
    static TriageTestCase rejected(String fuzzer, String expected, String path, String payload, int code, String message) {
        return testCase(1, """
                {
                  "testId": "Test 1", "result": "error", "fuzzer": "%s", "expectedResult": "%s",
                  "contractPath": "%s", "path": "%s",
                  "request": {"httpMethod": "POST", "url": "http://localhost:8080%s", "payload": %s, "sendBody": true},
                  "response": {"responseCode": %d, "jsonBody": %s}
                }
                """.formatted(fuzzer, expected, path, path, path, JsonUtils.GSON.toJson(payload), code, JsonUtils.GSON.toJson(message)));
    }

    static TriageTestCase withMutationTargets(TriageTestCase testCase, String... bodyFields) {
        testCase.testCase().getRequestProvenance().addMutationTargets(
                Arrays.stream(bodyFields).map(RequestTarget::body).toList());
        return testCase;
    }
}
