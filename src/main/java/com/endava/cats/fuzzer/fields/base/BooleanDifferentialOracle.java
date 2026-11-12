package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.SecurityFinding;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Verifies boolean-based blind SQL, NoSQL, and LDAP injections by comparing differential payload pairs
 * against a fresh unfuzzed baseline and reproducing the full pair on replay.
 */
public final class BooleanDifferentialOracle {

    private BooleanDifferentialOracle() {
    }

    /**
     * Evaluates a divergent response against the operation's fresh baseline, its paired neutral probe,
     * a second baseline stability check, and a full pair replay.
     *
     * @param divergentFirst the response already received for {@code check.values().divergentValue()}
     * @param check          pair definition, probe values, transport callbacks, and metadata
     * @return a confirmed security finding when the boolean differential holds and reproduces
     */
    public static Optional<SecurityFinding> verify(CatsResponse divergentFirst, DifferentialCheck check) {
        if (!isEligibleDivergentStatus(divergentFirst, check.pair().require2xxDivergent())) {
            return Optional.empty();
        }
        ProbeValues values = check.values();
        CatsResponse baselineFirst = check.transport().initialBaseline().get();
        if (!is2xx(baselineFirst) || !differsFrom(baselineFirst, divergentFirst, values.originalValue(), values.divergentValue())) {
            return Optional.empty();
        }
        CatsResponse neutralFirst = check.transport().payloadSender().apply(values.neutralValue());
        if (!matchesBaseline(baselineFirst, neutralFirst, values.originalValue(), values.neutralValue())) {
            return Optional.empty();
        }
        return confirmWithReplay(baselineFirst, divergentFirst, check);
    }

    private static Optional<SecurityFinding> confirmWithReplay(CatsResponse baselineFirst, CatsResponse divergentFirst,
                                                               DifferentialCheck check) {
        ProbeValues values = check.values();
        CatsResponse baselineSecond = check.transport().freshBaseline().get();
        if (!matchesBaseline(baselineFirst, baselineSecond, values.originalValue(), values.originalValue())) {
            return Optional.empty();
        }
        CatsResponse neutralSecond = check.transport().payloadSender().apply(values.neutralValue());
        if (!matchesBaseline(baselineSecond, neutralSecond, values.originalValue(), values.neutralValue())) {
            return Optional.empty();
        }
        CatsResponse divergentSecond = check.transport().payloadSender().apply(values.divergentValue());
        if (!isReproducedDivergent(baselineSecond, divergentFirst, divergentSecond, check)) {
            return Optional.empty();
        }
        return Optional.of(buildFinding(baselineSecond, neutralSecond, divergentSecond, check));
    }

    private static boolean isReproducedDivergent(CatsResponse baselineSecond, CatsResponse divergentFirst,
                                                 CatsResponse divergentSecond, DifferentialCheck check) {
        if (!isEligibleDivergentStatus(divergentSecond, check.pair().require2xxDivergent())) {
            return false;
        }
        ProbeValues values = check.values();
        double replaySelfSim = ResponseSimilarity.similarity(divergentFirst, divergentSecond,
                new ResponseSimilarity.ComparisonInputs(values.divergentValue(), values.divergentValue(), values.originalValue()));
        return replaySelfSim >= ResponseSimilarity.SIMILAR_THRESHOLD
                && differsFrom(baselineSecond, divergentSecond, values.originalValue(), values.divergentValue());
    }

    static boolean isEligibleDivergentStatus(CatsResponse response, boolean require2xx) {
        if (response == null) {
            return false;
        }
        int code = response.getResponseCode();
        if (require2xx) {
            return ResponseCodeFamily.is2xxCode(code);
        }
        return ResponseCodeFamily.is2xxCode(code) || code == 401 || code == 403 || code == 404;
    }

    private static boolean is2xx(CatsResponse response) {
        return response != null && ResponseCodeFamily.is2xxCode(response.getResponseCode());
    }

    private static boolean matchesBaseline(CatsResponse baseline, CatsResponse candidate, String origValue, String sentValue) {
        if (!is2xx(candidate)) {
            return false;
        }
        double sim = ResponseSimilarity.similarity(baseline, candidate,
                new ResponseSimilarity.ComparisonInputs(origValue, sentValue, origValue));
        return sim >= ResponseSimilarity.SIMILAR_THRESHOLD;
    }

    private static boolean differsFrom(CatsResponse baseline, CatsResponse candidate, String origValue, String sentValue) {
        double sim = ResponseSimilarity.similarity(baseline, candidate,
                new ResponseSimilarity.ComparisonInputs(origValue, sentValue, origValue));
        return sim < ResponseSimilarity.DIFFERENT_THRESHOLD;
    }

    private static SecurityFinding buildFinding(CatsResponse baseline, CatsResponse neutral, CatsResponse divergent,
                                                DifferentialCheck check) {
        ProbeValues values = check.values();
        double neutralSim = ResponseSimilarity.similarity(baseline, neutral,
                new ResponseSimilarity.ComparisonInputs(values.originalValue(), values.neutralValue(), values.originalValue()));
        double divergentSim = ResponseSimilarity.similarity(baseline, divergent,
                new ResponseSimilarity.ComparisonInputs(values.originalValue(), values.divergentValue(), values.originalValue()));
        String message = String.format(Locale.ROOT,
                "Boolean-based blind %s injection confirmed (%s): neutral probe [%s] matched fresh baseline "
                        + "(HTTP %d, similarity=%.2f), whereas differential probe [%s] altered the response "
                        + "(HTTP %d, similarity=%.2f) and reproduced on replay.",
                check.meta().injectionType(), check.pair().label(), truncate(values.neutralValue()), neutral.getResponseCode(),
                neutralSim, truncate(values.divergentValue()), divergent.getResponseCode(), divergentSim);
        return new SecurityFinding(check.meta().reason(), message, FindingConfidence.CONFIRMED);
    }

    private static String truncate(String value) {
        return value.length() > 50 ? value.substring(0, 50) + "..." : value;
    }

    /**
     * Synthesized string values for a boolean differential check on one field.
     *
     * @param originalValue  unfuzzed field value
     * @param neutralValue   baseline-preserving payload value
     * @param divergentValue response-altering payload value
     */
    public record ProbeValues(String originalValue, String neutralValue, String divergentValue) {
    }

    /**
     * Transport suppliers for baseline and replay calls during boolean differential verification.
     *
     * @param initialBaseline memoized first fresh baseline supplier for the operation
     * @param freshBaseline   sends a fresh unfuzzed baseline request for stability verification
     * @param payloadSender   sends a given string value in the target field via replay
     */
    public record ProbeTransport(Supplier<CatsResponse> initialBaseline, Supplier<CatsResponse> freshBaseline,
                                 Function<String, CatsResponse> payloadSender) {
    }

    /**
     * Reporting metadata for a boolean differential finding.
     *
     * @param reason        canonical finding reason
     * @param injectionType injection family label
     */
    public record FindingMeta(String reason, String injectionType) {
    }

    /**
     * Complete specification of a boolean differential verification attempt.
     */
    public record DifferentialCheck(BooleanInjectionPair pair, ProbeValues values,
                                    ProbeTransport transport, FindingMeta meta) {
    }
}
