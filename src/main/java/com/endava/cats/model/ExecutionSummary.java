package com.endava.cats.model;

import java.util.Collections;
import java.util.LinkedHashMap;
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
                               String qualityGateDescription, RunOutcome outcome) {
    public ExecutionSummary {
        responseCodeDistribution = Collections.unmodifiableMap(new LinkedHashMap<>(responseCodeDistribution));
        topFailingPaths = Collections.unmodifiableMap(new LinkedHashMap<>(topFailingPaths));
    }

}
