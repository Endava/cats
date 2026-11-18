package com.endava.cats.command.triage;

import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.SecurityFindingType;

import java.util.Optional;

/** Displays the detector's existing evidence and confidence without inferring a vulnerability from acceptance alone. */
final class SecurityTriageClassifier {
    private SecurityTriageClassifier() {
    }

    static Optional<ClusterClassification> classify(ClusterClassifier.Candidate candidate) {
        TriageTestCase test = candidate.testCase();
        return findingType(test).map(type -> classification(test, type));
    }

    static Optional<SecurityFindingType> findingType(TriageTestCase test) {
        String reason = test.testCase().getResultReason();
        return "XSS payload accepted without validation".equals(reason)
                ? Optional.of(SecurityFindingType.XSS_INJECTION) : SecurityFindingType.fromReason(reason);
    }

    private static ClusterClassification classification(TriageTestCase test, SecurityFindingType type) {
        boolean confirmed = test.testCase().getConfidence() == FindingConfidence.CONFIRMED;
        String diagnosis = confirmed ? "The security detector recorded CONFIRMED evidence; inspect the sample for the exact proof."
                : "The detector reported a security signal requiring review; acceptance or reflection alone does not prove exploitation.";
        String nextCheck = type == SecurityFindingType.XSS_INJECTION
                ? "Inspect where this value is rendered in a browser and its output encoding or sanitization; JSON acceptance or reflection alone does not establish executable XSS."
                : "Inspect the recorded detector evidence and compare it with the unmodified request; reproduce the specific security behavior before deciding on a fix.";
        return ClusterClassification.of(confirmed ? "CONFIRMED_SECURITY_FINDING" : "SECURITY_REVIEW", confirmed ? 1 : 2, test,
                new ClusterClassification.Explanation(diagnosis, nextCheck)).notLiveVerifiable();
    }
}
