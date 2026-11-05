package com.endava.cats.command.triage;

import java.util.List;

/**
 * A deduplicated root cause grouping all failing tests that share the same category, operation and error signature.
 * {@code verificationStatus} is {@code FIXED}, {@code STILL_FAILING} or {@code INCONCLUSIVE} when {@code --verify}
 * is enabled, with {@code verificationNote} explaining inconclusive outcomes.
 */
public record RootCauseCluster(String category,
                               int severityRank,
                               String operation,
                               int statusCode,
                               int occurrences,
                               String diagnosis,
                               String remediation,
                               List<String> fuzzers,
                               List<String> sampleTestIds,
                               String curlReproducer,
                               String verificationStatus,
                               Integer verifiedStatusCode,
                               String verificationNote) {
}
