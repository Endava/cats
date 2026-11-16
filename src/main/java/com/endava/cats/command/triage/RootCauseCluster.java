package com.endava.cats.command.triage;

import java.util.List;

/**
 * A deduplicated root cause grouping all failing tests that share the same category, operation and error signature.
 * {@code errorTemplateId} and {@code errorTemplate} are the mined error template of the representative test.
 * {@code verificationStatus} is {@code FIXED}, {@code STILL_FAILING} or {@code INCONCLUSIVE} when {@code --verify}
 * is enabled, with {@code verificationNote} explaining inconclusive outcomes.
 * {@code attribution} is {@link #SAME_AS_BASELINE} when a member test failed with the same error signature and status
 * as an unmodified baseline seed request ({@code HappyPath} or {@code OnlyRequiredFields}) on the same operation.
 */
public record RootCauseCluster(String category,
                               int severityRank,
                               String operation,
                               int statusCode,
                               int occurrences,
                               String diagnosis,
                               String errorTemplateId,
                               String errorTemplate,
                               String remediation,
                               List<String> fuzzers,
                               List<String> sampleTestIds,
                               String curlReproducer,
                               String verificationStatus,
                               Integer verifiedStatusCode,
                               String verificationNote,
                               String attribution) {
    public static final String SAME_AS_BASELINE = "SAME_AS_BASELINE";

    public RootCauseCluster(String category,
                            int severityRank,
                            String operation,
                            int statusCode,
                            int occurrences,
                            String diagnosis,
                            String errorTemplateId,
                            String errorTemplate,
                            String remediation,
                            List<String> fuzzers,
                            List<String> sampleTestIds,
                            String curlReproducer,
                            String verificationStatus,
                            Integer verifiedStatusCode,
                            String verificationNote) {
        this(category, severityRank, operation, statusCode, occurrences, diagnosis, errorTemplateId, errorTemplate,
                remediation, fuzzers, sampleTestIds, curlReproducer, verificationStatus, verifiedStatusCode,
                verificationNote, null);
    }
}
