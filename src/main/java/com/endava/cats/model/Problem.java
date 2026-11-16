package com.endava.cats.model;

import lombok.Builder;

import java.util.List;

/**
 * A set of failing tests that share one problem key: the evidence the oracle judged, never the response body by
 * default. Problems span operations; each {@link Occurrence} is the problem on one operation. Problems are computed
 * once when the report is written and persisted in {@code cats-summary-report.json}.
 *
 * @param id              8 hex characters of the SHA-256 of the versioned problem key
 * @param kind            what the oracle judged
 * @param title           a short, kind-specific title; never a body-derived headline for non-message kinds
 * @param discriminator   the key parts after the kind, for display and diagnostics
 * @param level           {@code error} when any test is an error, {@code warn} otherwise
 * @param reasons         the distinct result reasons, most frequent first
 * @param testCount       the number of tests
 * @param errorTemplateId the server message id, for {@link ProblemKind#isIdentifiedByServerMessage()} kinds only
 * @param errorTemplate   the server message (masked), for {@link ProblemKind#isIdentifiedByServerMessage()} kinds only
 * @param occurrences     the problem per operation, most tests first
 * @param evidence        the evidence of the most confident test, for accepted requests and response violations
 * @param variants        distinct server messages (status-code problems) or violations (schema mismatches)
 * @param alsoIn          ids of other problems with the same server message or found by the same fuzzer
 * @param sameAsBaseline  the number of tests that also fail the same way on the unmodified request
 */
@Builder(toBuilder = true)
public record Problem(String id, ProblemKind kind, String title, String discriminator, String level,
                      List<String> reasons, int testCount, String errorTemplateId, String errorTemplate,
                      List<Occurrence> occurrences, Evidence evidence, List<Variant> variants,
                      List<String> alsoIn, int sameAsBaseline) {

    /**
     * The version of the rules that compute the problem key. Readers must not compare problem ids across versions.
     */
    public static final int KEY_VERSION = 1;

    /**
     * Whether any test of the problem is an error.
     *
     * @return {@code true} for error problems
     */
    public boolean isError() {
        return CatsResultFactory.ERROR.equals(level);
    }

    /**
     * One problem on one operation.
     *
     * @param operation the HTTP method and contract path, e.g. {@code POST /orders}
     * @param fields    the mutated request fields with their test counts, most tests first
     * @param testIds   the ids of the tests, in execution order
     * @param statuses  the distinct HTTP status codes, ascending
     * @param fuzzers   the distinct fuzzers, most tests first
     */
    public record Occurrence(String operation, List<FieldCount> fields, List<String> testIds, List<Integer> statuses,
                             List<String> fuzzers) {
    }

    /**
     * A mutated request field, e.g. {@code body.description}, with the number of tests that mutated it.
     *
     * @param field the field
     * @param tests the number of tests
     */
    public record FieldCount(String field, int tests) {
    }

    /**
     * The evidence of the most confident test of a problem.
     *
     * @param testId     the test id
     * @param confidence the finding confidence, or {@code null} when the check has none
     * @param details    the result details of the test
     */
    public record Evidence(String testId, FindingConfidence confidence, String details) {
    }

    /**
     * A distinct server message or violation within a problem.
     *
     * @param errorTemplateId the server message id, or {@code null} for a violation
     * @param text            the server message or the violation
     * @param count           the number of tests
     */
    public record Variant(String errorTemplateId, String text, int count) {
    }
}
