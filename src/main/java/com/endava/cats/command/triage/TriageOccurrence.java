package com.endava.cats.command.triage;

import lombok.Builder;

import java.util.List;

/**
 * One problem on one operation, classified by the triage strategies. The classification is the one most of its tests
 * get; the representative (reproducer, {@code --verify}) is the lowest-numbered test with that classification.
 * {@code verificationStatus} is {@code FIXED}, {@code STILL_FAILING} or {@code INCONCLUSIVE} with {@code --verify}.
 * {@code attribution} is {@link #SAME_AS_BASELINE} when a test failed the same way as an unmodified seed request
 * ({@code HappyPath} or {@code OnlyRequiredFields}) on the same operation.
 *
 * @param operation          the HTTP method and contract path
 * @param statusCodes        the HTTP status codes of its triaged tests
 * @param testCount          the number of triaged tests
 * @param category           the triage category (observation or hypothesis)
 * @param severityRank       0 for server errors, higher for less severe categories
 * @param diagnosis          what happened
 * @param remediation        what to do
 * @param fuzzers            the fuzzers that found it
 * @param fields             the mutated request fields
 * @param sampleTestIds      up to 5 test ids
 * @param curlReproducer     a curl command for the representative test
 * @param verificationStatus the live replay outcome, or {@code null} without {@code --verify}
 * @param verifiedStatusCode the status code of the live replay
 * @param verificationNote   why a replay was inconclusive
 * @param attribution        {@link #SAME_AS_BASELINE} or {@code null}
 * @param sample             expectations, confidence and evidence from the representative test
 */
@Builder(toBuilder = true)
public record TriageOccurrence(String operation, List<Integer> statusCodes, int testCount, String category, int severityRank,
                               String diagnosis, String remediation, List<String> fuzzers, List<String> fields,
                               List<String> sampleTestIds, String curlReproducer, String verificationStatus,
                               Integer verifiedStatusCode, String verificationNote, String attribution, TriageSample sample) {
    public static final String SAME_AS_BASELINE = "SAME_AS_BASELINE";
}
