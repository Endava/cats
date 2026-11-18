package com.endava.cats.command.triage;

import com.endava.cats.model.ExpectedResponse;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.Problem;
import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class TriageDiagnosisTest {
    @ParameterizedTest
    @CsvSource(value = {
            "501|Unexpected response code: 501|405|METHOD_NOT_IMPLEMENTED_501|routing",
            "500|Unexpected response code: 500|2XX|SERVER_CRASH_5XX|logs",
            "953|Connection refused|2XX|TRANSPORT_FAILURE|reachability",
            "959|Call timeout exceeded|2XX|TRANSPORT_FAILURE|reachability",
            "999|Contract check failed|2XX|NON_HTTP_RESULT|reported check",
            "429|Rate limited|2XX|RATE_LIMITED|--maxRequestsPerMinute",
            "503|Rate limited|2XX|RATE_LIMITED|Retry-After",
            "503|Unexpected response code: 503|2XX|SERVER_CRASH_5XX|logs",
            "200|Response body truncated|2XX|RESPONSE_TRUNCATED|--maxResponseBytes",
            "401|Response content type not matching the contract|401|RESPONSE_CONTENT_TYPE_MISMATCH|Content-Type",
            "401|Unexpected response code: 401|401|CONTRACT_MISMATCH|matches the recorded status",
            "401|Unexpected response code: 401|2XX|AUTH_CONFIGURATION_GAP|permissions",
            "403|Not matching response schema|403|RESPONSE_SCHEMA_MISMATCH|schema violations",
            "200|Missing response headers|2XX|MISSING_RESPONSE_HEADERS|missing header names",
            "200|Response time exceeds max|2XX|RESPONSE_TIME_LIMIT|latency",
            "200|XSS payload accepted without validation|4XX|SECURITY_REVIEW|output encoding",
            "200|XSS payload reflected in response|4XX|SECURITY_REVIEW|executable XSS",
            "200|Unexpected response code: 200|4XX|VALIDATION_BYPASS_2XX|constraint tested",
            "422|Unexpected response code: 422|405|CONTRACT_MISMATCH|validation order"
    }, delimiter = '|')
    void shouldTieTheNextCheckToTheRecordedFailure(int code, String reason, String expected, String category, String nextCheck) {
        TriageTestCase test = sample(code, reason, expected);

        ClusterClassification result = classifier().classify(candidate(test));

        assertThat(result.category()).isEqualTo(category);
        assertThat(result.remediation()).contains(nextCheck);
        assertThat(test.testCase().getConfidence()).isNull();
    }

    @Test
    void shouldPreferStructuredExpectationsOverLegacyTextAndAvoidAnAuthDiagnosis() {
        TriageTestCase test = sample(401, "Unexpected response code: 401", "2XX");
        test.testCase().recordExpectedResponse(new ExpectedResponse(List.of("401")));

        ClusterClassification result = classifier().classify(candidate(test));

        assertThat(result.category()).isEqualTo("CONTRACT_MISMATCH");
        assertThat(result.remediation()).contains("matches the recorded status expectation");
    }

    @ParameterizedTest
    @CsvSource({"CONFIRMED,CONFIRMED_SECURITY_FINDING", "FIRM,SECURITY_REVIEW", "TENTATIVE,SECURITY_REVIEW"})
    void shouldPreserveDetectorConfidenceAndNeverVerifySecurityWithOnlyAStatus(FindingConfidence confidence, String category) {
        TriageTestCase test = sample(200, "XSS payload reflected in response", "4XX");
        test.testCase().setConfidence(confidence);
        ClusterClassification result = classifier().classify(candidate(test));

        LiveClusterVerifier.Verification verification = new LiveClusterVerifier(null, Map.of(), _ -> null).verify(result, test);

        assertThat(result.category()).isEqualTo(category);
        assertThat(test.testCase().getConfidence()).isEqualTo(confidence);
        assertThat(verification.status()).isEqualTo("INCONCLUSIVE");
        assertThat(verification.statusCode()).isNull();
        assertThat(verification.note()).contains("security fix");
    }

    @Test
    void shouldNotMistakeAStatusOnlyReplayForAResponseCheckFix() {
        TriageTestCase test = sample(401, "Response content type not matching the contract", "401");
        ClusterClassification result = classifier().classify(candidate(test));

        LiveClusterVerifier.Verification verification = new LiveClusterVerifier(null, Map.of(), _ -> null).verify(result, test);

        assertThat(verification.status()).isEqualTo("INCONCLUSIVE");
        assertThat(verification.note()).contains("response check");
        assertThat(verification.statusCode()).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "XSS payload accepted without validation|API accepted XSS payload (response code 200). For APIs, implement input validation.|API accepted XSS payload (response code 200)",
            "XSS payload reflected in response|Dangerous XSS payload was reflected in the response. Implement input validation.|Dangerous XSS payload was reflected in the response",
            "Not matching response schema|Response code 200 is documented. Validation errors: $.name: string expected, number found|$.name: string expected, number found"
    }, delimiter = '|')
    void shouldDisplayRecordedObservationsWithoutRepeatingLegacyAdvice(String reason, String details, String evidence) {
        TriageTestCase test = sample(200, reason, "2XX");
        test.testCase().setResultDetails(details);

        assertThat(TriageSample.from(candidate(test)).evidence()).isEqualTo(evidence);
        assertThat(test.testCase().getResultDetails()).isEqualTo(details);
    }

    @Test
    void shouldKeepTheDisplayedRepresentativeConsistentWithTheDiagnosisAndCurl() {
        List<ClusterClassifier.Candidate> candidates = IntStream.rangeClosed(1, 11).mapToObj(TriageDiagnosisTest::numberedCandidate).toList();
        var occurrence = new Problem.Occurrence("POST /items", List.of(), candidates.stream().map(c -> c.testCase().testCase().getTestId()).toList(),
                List.of(400), List.of("Probe"));

        TriageOccurrence result = new OccurrenceTriage(classifier(), Optional.empty()).triage(occurrence, candidates).orElseThrow();

        assertThat(result.category()).isEqualTo("RESPONSE_CONTENT_TYPE_MISMATCH");
        assertThat(result.testCount()).isEqualTo(11);
        assertThat(result.sampleTestIds()).hasSize(5).startsWith("Test 6");
        assertThat(result.sample().testId()).isEqualTo("Test 6");
        assertThat(result.sample().expectedResponseCodes()).containsExactly("2XX");
        assertThat(result.curlReproducer()).contains("sample=6");
    }

    @Test
    void shouldExposeEvidenceAndExpectationsWithoutChangingSavedProblems(@TempDir Path folder) throws IOException {
        TriageTestCase test = sample(401, "Response content type not matching the contract", "401");
        test.testCase().setResultDetails("Expected application/json; received text/html");
        Files.writeString(folder.resolve("Test1.json"), JsonUtils.GSON.toJson(test.testCase()));
        TriageReportFixtures.writeSummary(folder);
        Problem saved = TriageEngine.readProblems(folder).getProblems().getFirst();

        TriageReport result = new TriageEngine(new TriageOptions(false, false, null, Map.of(), _ -> null)).triage(folder, null);

        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.problemId()).isEqualTo(saved.id());
            assertThat(problem.kind()).isEqualTo(saved.kind());
            assertThat(problem.testCount()).isEqualTo(saved.testCount());
            assertThat(problem.occurrences().getFirst().sample().evidence()).contains("application/json", "text/html");
        });
        assertThat(JsonUtils.GSON.toJson(result)).contains("\"expectedResponseCodes\"", "\"curlReproducer\"", "\"statusCode\": 401");
    }

    private static ClusterClassifier.Candidate numberedCandidate(int number) {
        TriageTestCase test = sample(400, number < 6 ? "Unexpected response code: 400" : "Response content type not matching the contract", "2XX");
        test.testCase().setTestId("Test " + number);
        test.testCase().getRequest().setUrl("http://localhost/items?sample=" + number);
        return candidate(new TriageTestCase(number, test.testCase()));
    }

    private static TriageTestCase sample(int code, String reason, String expected) {
        TriageTestCase test = TriageFixtures.rejected("Probe", expected, "/items", "{}", code, "Recorded server message");
        test.testCase().setResultReason(reason);
        return test;
    }

    private static ClusterClassifier.Candidate candidate(TriageTestCase test) {
        return new ClusterClassifier.Candidate(test, ErrorMessage.of(test), Optional.empty());
    }

    private static ClusterClassifier classifier() {
        return new ClusterClassifier(new ClassificationContext(Set.of(), Map.of(), Map.of(), Map.of(), Set.of(), new ContractDriftDetector(null), null));
    }
}
