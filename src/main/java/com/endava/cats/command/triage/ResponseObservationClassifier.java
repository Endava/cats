package com.endava.cats.command.triage;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;

import java.util.Optional;

/** Gives recorded check failures precedence over guesses based only on an HTTP status. */
final class ResponseObservationClassifier {
    private ResponseObservationClassifier() {
    }

    static Optional<ClusterClassification> classify(ClusterClassifier.Candidate candidate) {
        TriageTestCase test = candidate.testCase();
        if (CatsResponse.ExceptionalResponse.fromResponseCode(test.statusCode()).isPresent()) {
            return Optional.of(observation(test, "TRANSPORT_FAILURE", "No HTTP response was obtained (CATS code %d).".formatted(test.statusCode()),
                    "Check the recorded network error, service reachability and server logs; repeat the request before assessing API behavior."));
        }
        if (test.statusCode() < 100 || test.statusCode() >= 600) {
            return Optional.of(observation(test, "NON_HTTP_RESULT", "This saved check has no HTTP response (CATS code %d).".formatted(test.statusCode()),
                    "Inspect the sample's fuzzer and reported check; contract-linter results require inspecting the contract rather than replaying an HTTP response."));
        }
        if (test.testCase().getResponse().isBodyTruncated()) {
            return Optional.of(truncated(test));
        }
        return Reason.fromResultReason(test.testCase().getResultReason()).flatMap(reason -> fromReason(test, reason));
    }

    private static Optional<ClusterClassification> fromReason(TriageTestCase test, Reason reason) {
        return switch (reason) {
            case RATE_LIMITED -> Optional.of(observation(test, "RATE_LIMITED", "Rate limiting prevented a conclusive test verdict.",
                    "Reduce --maxRequestsPerMinute, respect Retry-After and re-run after the rate limit resets; this result does not establish a contract defect."));
            case RESPONSE_BODY_TRUNCATED -> Optional.of(truncated(test));
            case CALL_TIMEOUT_EXCEEDED -> Optional.of(observation(test, "CALL_TIMEOUT", "The request exceeded the configured call budget.",
                    "Check service latency and --callTimeout; re-run with a suitable budget before assessing the response."));
            case RESPONSE_TIME_EXCEEDS_MAX -> Optional.of(observation(test, "RESPONSE_TIME_LIMIT", "The recorded response exceeded --maxResponseTimeInMs.",
                    "Compare the recorded latency with the configured limit and server traces; repeat under the intended load."));
            default -> Optional.empty();
        };
    }

    static Optional<ClusterClassification> assertion(ClusterClassifier.Candidate candidate) {
        return Reason.fromResultReason(candidate.testCase().testCase().getResultReason())
                .flatMap(reason -> responseAssertion(candidate.testCase(), reason));
    }

    private static Optional<ClusterClassification> responseAssertion(TriageTestCase test, Reason reason) {
        return switch (reason) {
            case RESPONSE_CONTENT_TYPE_NOT_MATCHING -> Optional.of(observation(test, "RESPONSE_CONTENT_TYPE_MISMATCH",
                    "The response Content-Type did not match the contract.",
                    "Compare the recorded Content-Type with the response media types for HTTP %d in the original contract; check gateway error pages and content negotiation."
                            .formatted(test.statusCode())));
            case NOT_MATCHING_RESPONSE_SCHEMA -> Optional.of(observation(test, "RESPONSE_SCHEMA_MISMATCH",
                    "The response body did not match its documented schema.",
                    "Inspect the reported schema violations in the sample test, then compare the response fields and types with the original contract."));
            case MISSING_RESPONSE_HEADERS -> Optional.of(observation(test, "MISSING_RESPONSE_HEADERS",
                    "The response omitted headers documented in the contract.",
                    "Inspect the missing header names in the sample test and confirm which layer should supply them for this response."));
            default -> Optional.empty();
        };
    }

    private static ClusterClassification truncated(TriageTestCase test) {
        return observation(test, "RESPONSE_TRUNCATED", "CATS could not complete body-dependent checks because the response was truncated.",
                "Review --maxResponseBytes and the response size; re-run with a suitable capture limit before judging the body or schema.");
    }

    private static ClusterClassification observation(TriageTestCase test, String category, String diagnosis, String nextCheck) {
        return ClusterClassification.of(category, 2, test, new ClusterClassification.Explanation(diagnosis, nextCheck)).notLiveVerifiable();
    }
}
