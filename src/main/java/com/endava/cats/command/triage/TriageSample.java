package com.endava.cats.command.triage;

import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.CatsResultFactory.Reason;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/** Evidence from the same representative used for the diagnosis, curl and optional replay. */
public record TriageSample(String testId, int statusCode, List<String> expectedResponseCodes, FindingConfidence confidence, String evidence) {
    static TriageSample from(ClusterClassifier.Candidate candidate) {
        TriageTestCase test = candidate.testCase();
        String detail = StringUtils.defaultIfBlank(test.testCase().getResultDetails(), candidate.message().signature());
        String evidence = StringUtils.abbreviate(StringUtils.normalizeSpace(observedDetail(test, detail)), 220);
        return new TriageSample(test.testCase().getTestId(), test.statusCode(), test.expected().tokens(), test.testCase().getConfidence(), evidence);
    }

    private static String observedDetail(TriageTestCase test, String detail) {
        if (detail.startsWith("API accepted XSS payload") || detail.startsWith("Dangerous XSS payload was reflected")) {
            return StringUtils.substringBefore(detail, ". ");
        }
        if (Reason.fromResultReason(test.testCase().getResultReason()).filter(Reason.NOT_MATCHING_RESPONSE_SCHEMA::equals).isPresent()
                && detail.contains("Validation errors: ")) {
            return StringUtils.substringAfter(detail, "Validation errors: ");
        }
        return detail;
    }
}
