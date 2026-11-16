package com.endava.cats.report.problem;

import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.Problem;
import com.endava.cats.model.ProblemKind;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * The identity of a problem: what the oracle judged, never the response body by default. It is the single function
 * that decides which tests are the same problem, so the report, its test pages, triage and compare cannot disagree.
 *
 * <p>Precedence: a 5xx response is a {@link ProblemKind#SERVER_ERROR}, keyed by its server message whatever the result
 * reason. The other kinds follow {@link ProblemKind#of(int, String)}; accepted requests are keyed by the fuzzer, which
 * is CATS's name for the input class that was accepted, and schema mismatches by the declared response schema.</p>
 *
 * @param kind  what the oracle judged
 * @param parts the kind-specific discriminators
 */
public record ProblemKey(ProblemKind kind, List<String> parts) {

    /**
     * Computes the problem key of a failing or warning test.
     *
     * @param testCase the test case summary
     * @param schemas  the declared response schemas of the contract
     * @return the problem key
     */
    public static ProblemKey of(CatsTestCaseSummary testCase, ResponseSchemaNames schemas) {
        ProblemKind kind = ProblemKind.of(testCase.getHttpResponseCode(), testCase.getResultReason());
        return switch (kind) {
            case SERVER_ERROR -> new ProblemKey(kind, List.of(templateId(testCase)));
            case ERROR_MESSAGE -> new ProblemKey(kind, List.of(ResultReasons.verdictFamily(testCase.getResultReason()), templateId(testCase)));
            case RESPONSE_VIOLATION -> responseViolation(testCase, schemas);
            case STATUS_CODE, NO_RESPONSE -> new ProblemKey(kind, List.of(ResultReasons.family(testCase.getResultReason()),
                    String.valueOf(testCase.getHttpResponseCode())));
            case ACCEPTED_REQUEST -> new ProblemKey(kind, List.of(ResultReasons.verdictFamily(testCase.getResultReason()),
                    testCase.getHttpResponseCode() / 100 + "xx", StringUtils.defaultString(testCase.getFuzzer())));
        };
    }

    /**
     * Returns the problem id: 8 hex characters of the SHA-256 of {@code v1|kind|parts}.
     *
     * @return the problem id
     */
    public String id() {
        String key = String.join("|", "v" + Problem.KEY_VERSION, kind.name(), String.join("|", parts));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Returns the discriminators as one display string.
     *
     * @return the discriminators joined by {@code " · "}
     */
    public String discriminator() {
        return String.join(" · ", parts);
    }

    private static ProblemKey responseViolation(CatsTestCaseSummary testCase, ResponseSchemaNames schemas) {
        String reason = ResultReasons.family(testCase.getResultReason());
        String violation = ResultReasons.is(testCase.getResultReason(), Reason.NOT_MATCHING_RESPONSE_SCHEMA)
                ? schemas.nameOf(testCase.getHttpMethod(), testCase.getPath(), testCase.getHttpResponseCode())
                : Violations.of(testCase.getResultDetails());
        return new ProblemKey(ProblemKind.RESPONSE_VIOLATION, List.of(reason, violation));
    }

    private static String templateId(CatsTestCaseSummary testCase) {
        return StringUtils.defaultString(testCase.getErrorTemplateId());
    }
}
