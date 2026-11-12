package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.SecurityFinding;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies time-based blind SQL, Command, and NoSQL injections without relying on response text.
 * <p>
 * Combines EvoMaster's baseline/delay rule (unfuzzed baseline {@code < 2s}, injected {@code >= S} seconds)
 * with OWASP ZAP {@code TimingUtils}'s alternating high/low delay linear-regression correlation check.
 * </p>
 */
public final class TimingInjectionOracle {
    static final long MAX_BASELINE_MS = 2_000L;
    static final int MIN_HIGH_DELAY_SECONDS = 5;
    static final int LOW_DELAY_SECONDS = 1;
    private static final long CLOCK_JITTER_MS = 50L;
    private static final double INTERMEDIATE_CORRELATION_ERROR = 0.30;
    private static final double INTERMEDIATE_SLOPE_ERROR = 0.50;
    private static final double FINAL_CORRELATION_ERROR = 0.15;
    private static final double FINAL_SLOPE_ERROR = 0.30;

    private static final Pattern SQL_SLEEP = Pattern.compile("(?i)((?:pg_)?sleep\\(\\s*)(\\d{1,2})(\\s*\\))");
    private static final Pattern WAITFOR_DELAY = Pattern.compile("(?i)(waitfor\\s+delay\\s+'0:0:)(\\d{1,2})(')");
    private static final Pattern NOSQL_MS_SLEEP = Pattern.compile("(?i)(\\bsleep\\(\\s*)(\\d{4,5})(\\s*\\))");
    private static final Pattern SHELL_SLEEP = Pattern.compile("(?i)(\\bsleep\\s+)(\\d{1,2})(\\b)");

    private TimingInjectionOracle() {
    }

    /**
     * Parses a sleep/delay payload into a parameterizable {@link TimingSpec} when its configured delay is at least 5s.
     *
     * @param payload the candidate injection payload
     * @return the timing specification, or empty when the payload is not a time-based probe
     */
    public static Optional<TimingSpec> parseTimingSpec(String payload) {
        if (payload == null || payload.isEmpty()) {
            return Optional.empty();
        }
        return matchMillisSleep(payload)
                .or(() -> matchSecondsPattern(payload, SQL_SLEEP))
                .or(() -> matchSecondsPattern(payload, WAITFOR_DELAY))
                .or(() -> matchSecondsPattern(payload, SHELL_SLEEP));
    }

    private static Optional<TimingSpec> matchMillisSleep(String payload) {
        Matcher matcher = NOSQL_MS_SLEEP.matcher(payload);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int delaySeconds = Integer.parseInt(matcher.group(2)) / 1_000;
        if (delaySeconds < MIN_HIGH_DELAY_SECONDS) {
            return Optional.empty();
        }
        String prefix = payload.substring(0, matcher.start(2));
        String suffix = payload.substring(matcher.end(2));
        return Optional.of(new TimingSpec(delaySeconds, seconds -> prefix + (seconds * 1_000) + suffix));
    }

    private static Optional<TimingSpec> matchSecondsPattern(String payload, Pattern pattern) {
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int delaySeconds = Integer.parseInt(matcher.group(2));
        if (delaySeconds < MIN_HIGH_DELAY_SECONDS) {
            return Optional.empty();
        }
        String prefix = payload.substring(0, matcher.start(2));
        String suffix = payload.substring(matcher.end(2));
        return Optional.of(new TimingSpec(delaySeconds, seconds -> prefix + seconds + suffix));
    }

    /**
     * Verifies a candidate slow response using a fresh baseline check followed by alternating low/high delay probes.
     *
     * @param initialResponse the response observed for the initial high-delay payload
     * @param spec            the parsed timing specification of that payload
     * @param context         baseline supplier, probe sender, and reporting metadata
     * @return a confirmed security finding if delays correlate linearly with requested sleep durations
     */
    public static Optional<SecurityFinding> verify(CatsResponse initialResponse, TimingSpec spec, TimingContext context) {
        if (!meetsRequestedDelay(initialResponse, spec.highDelaySeconds())) {
            return Optional.empty();
        }
        long baselineMs = context.baselineTimeMs().getAsLong();
        if (baselineMs < 0 || baselineMs >= MAX_BASELINE_MS) {
            return Optional.empty();
        }
        OnlineLinearRegression regression = new OnlineLinearRegression();
        regression.addPoint(spec.highDelaySeconds(), toSeconds(initialResponse));
        if (!runAlternatingProbes(regression, spec.highDelaySeconds(), context.delayProbeSender())) {
            return Optional.empty();
        }
        if (!regression.withinConfidence(FINAL_CORRELATION_ERROR, 1.0, FINAL_SLOPE_ERROR)) {
            return Optional.empty();
        }
        return Optional.of(buildFinding(initialResponse, new TimingStats(baselineMs, spec.highDelaySeconds(), regression), context));
    }

    private static boolean runAlternatingProbes(OnlineLinearRegression regression, int highSeconds,
                                                IntFunction<CatsResponse> probeSender) {
        for (int requestedSeconds : List.of(LOW_DELAY_SECONDS, highSeconds, LOW_DELAY_SECONDS)) {
            CatsResponse probe = probeSender.apply(requestedSeconds);
            if (!isAcceptableProbe(probe, requestedSeconds)) {
                return false;
            }
            regression.addPoint(requestedSeconds, toSeconds(probe));
            if (!regression.withinConfidence(INTERMEDIATE_CORRELATION_ERROR, 1.0, INTERMEDIATE_SLOPE_ERROR)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAcceptableProbe(CatsResponse response, int requestedSeconds) {
        if (!meetsRequestedDelay(response, requestedSeconds)) {
            return false;
        }
        return requestedSeconds != LOW_DELAY_SECONDS
                || response.getResponseTimeInMs() < MAX_BASELINE_MS + LOW_DELAY_SECONDS * 1_000L;
    }

    static boolean isHttpResponse(CatsResponse response) {
        return response != null && ResponseCodeFamily.isValidHttpCode(response.getResponseCode());
    }

    private static boolean meetsRequestedDelay(CatsResponse response, int requestedSeconds) {
        return isHttpResponse(response)
                && response.getResponseTimeInMs() >= requestedSeconds * 1_000L - CLOCK_JITTER_MS;
    }

    private static double toSeconds(CatsResponse response) {
        return response.getResponseTimeInMs() / 1_000.0;
    }

    private static SecurityFinding buildFinding(CatsResponse initialResponse, TimingStats stats, TimingContext context) {
        String message = String.format(Locale.ROOT,
                "Time-based blind %s injection confirmed: unfuzzed baseline took %d ms (< %d ms), initial %ds payload took %d ms, "
                        + "and alternating %ds/%ds probes correlated linearly (r^2=%.2f, slope=%.2f). Response code: %d.",
                context.injectionType(), stats.baselineMs(), MAX_BASELINE_MS, stats.highSeconds(),
                initialResponse.getResponseTimeInMs(), stats.highSeconds(), LOW_DELAY_SECONDS,
                stats.regression().rSquared(), stats.regression().slope(), initialResponse.getResponseCode());
        return new SecurityFinding(context.reason(), message, FindingConfidence.CONFIRMED);
    }

    private record TimingStats(long baselineMs, int highSeconds, OnlineLinearRegression regression) {
    }

    /**
     * Parameterized timing payload descriptor.
     *
     * @param highDelaySeconds the initial high delay in seconds
     * @param payloadBuilder   builds a payload variant for a given delay in seconds
     */
    public record TimingSpec(int highDelaySeconds, IntFunction<String> payloadBuilder) {
    }

    /**
     * Execution callbacks and finding metadata for timing verification.
     *
     * @param reason           canonical finding reason
     * @param injectionType    injection family label
     * @param baselineTimeMs   returns unfuzzed baseline time in ms, or {@code -1} when unavailable
     * @param delayProbeSender sends the payload variant for a given delay in seconds
     */
    public record TimingContext(String reason, String injectionType, LongSupplier baselineTimeMs,
                                IntFunction<CatsResponse> delayProbeSender) {
    }
}
