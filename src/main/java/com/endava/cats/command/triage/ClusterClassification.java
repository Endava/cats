package com.endava.cats.command.triage;

/**
 * The root cause assigned to one failing test; an occurrence takes the classification of most of its tests.
 *
 * @param liveVerifiable {@code false} when a live replay cannot prove a fix (e.g. contract-only drift)
 */
record ClusterClassification(String category,
                             int severityRank,
                             String operation,
                             int statusCode,
                             String diagnosis,
                             String remediation,
                             boolean liveVerifiable) {

    static ClusterClassification of(String category, int severityRank, TriageTestCase testCase, Explanation explanation) {
        return new ClusterClassification(category, severityRank, testCase.operation(), testCase.statusCode(),
                explanation.diagnosis(), explanation.remediation(), true);
    }

    ClusterClassification notLiveVerifiable() {
        return new ClusterClassification(category, severityRank, operation, statusCode, diagnosis, remediation, false);
    }

    /**
     * Diagnosis and remediation text of a classification.
     */
    record Explanation(String diagnosis, String remediation) {
    }
}
