package com.endava.cats.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable execution statistics shared by reports and presentation layers.
 * Reported results are tests included in reports. Results intentionally omitted by reporting options are tracked
 * separately, while ordinary control-flow skips are not part of execution accounting.
 */
public record ExecutionSummary(long requestsAttempted, long reportedResults, long success, long warnings, long errors,
                               long skippedFromReporting, int authenticationErrors, int ioErrors,
                               Map<Integer, Integer> responseCodeDistribution,
                               Map<String, Long> topFailingPaths, boolean qualityGatePassed,
                               String qualityGateDescription, RunOutcome outcome,
                               long throttledRequests, long throttledRequestsRetried, int throttledOperations,
                               int effectiveRequestsPerMinute, int configuredRequestsPerMinute,
                               List<String> rateLimitedOperations) {
    public ExecutionSummary {
        responseCodeDistribution = Collections.unmodifiableMap(new LinkedHashMap<>(responseCodeDistribution));
        topFailingPaths = Collections.unmodifiableMap(new LinkedHashMap<>(topFailingPaths));
        rateLimitedOperations = rateLimitedOperations == null ? List.of() : List.copyOf(rateLimitedOperations);
    }

    public ExecutionSummary(long requestsAttempted, long reportedResults, long success, long warnings, long errors,
                            long skippedFromReporting, int authenticationErrors, int ioErrors,
                            Map<Integer, Integer> responseCodeDistribution,
                            Map<String, Long> topFailingPaths, boolean qualityGatePassed,
                            String qualityGateDescription, RunOutcome outcome) {
        this(requestsAttempted, reportedResults, success, warnings, errors, skippedFromReporting,
                authenticationErrors, ioErrors, responseCodeDistribution, topFailingPaths,
                qualityGatePassed, qualityGateDescription, outcome, 0L, 0L, 0, 0, 0, List.of());
    }

    public long totalTestsExecuted() {
        return reportedResults + skippedFromReporting;
    }

    public String throttlingSummary() {
        if (throttledRequests <= 0) {
            return "";
        }
        if (effectiveRequestsPerMinute > 0 && configuredRequestsPerMinute > 0
                && effectiveRequestsPerMinute < configuredRequestsPerMinute) {
            return "%d requests throttled (%d retried successfully) on %d operations; effective rate lowered to %d rpm"
                    .formatted(throttledRequests, throttledRequestsRetried, throttledOperations, effectiveRequestsPerMinute);
        }
        if (effectiveRequestsPerMinute > 0) {
            return "%d requests throttled (%d retried successfully) on %d operations; effective rate %d rpm"
                    .formatted(throttledRequests, throttledRequestsRetried, throttledOperations, effectiveRequestsPerMinute);
        }
        return "%d requests throttled (%d retried successfully) on %d operations"
                .formatted(throttledRequests, throttledRequestsRetried, throttledOperations);
    }

    public String rateLimitedOperationsHint() {
        if (rateLimitedOperations.isEmpty()) {
            return "";
        }
        return "More than 50%% of final responses on %s were HTTP 429 Too Many Requests; use --maxRequestsPerMinute to lower the request rate."
                .formatted(String.join(", ", rateLimitedOperations));
    }
}
