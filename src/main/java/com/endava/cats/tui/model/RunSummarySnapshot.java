package com.endava.cats.tui.model;

import com.endava.cats.report.ExecutionStatisticsListener;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable aggregate statistics captured when a CATS session completes.
 */
public record RunSummarySnapshot(long totalRequests, long reportedResults, long success, long warnings, long errors,
                                 int skipped, long skippedFromReporting, int authenticationErrors, int ioErrors,
                                 Map<Integer, Integer> responseCodeDistribution,
                                 Map<String, Long> topFailingPaths, boolean qualityGatePassed,
                                 String qualityGateDescription) {
    /**
     * Creates a final summary from the execution statistics listener.
     *
     * @param statistics source statistics
     * @param qualityGatePassed whether the configured quality gate passed
     * @param qualityGateDescription human-readable configured quality gate
     * @return immutable final summary
     */
    public static RunSummarySnapshot from(ExecutionStatisticsListener statistics, boolean qualityGatePassed,
                                          String qualityGateDescription) {
        return new RunSummarySnapshot(statistics.getTotalRequests(), statistics.getAll(), statistics.getSuccess(),
                statistics.getWarns(), statistics.getErrors(), statistics.getSkipped(),
                statistics.getSkippedFromReporting(), statistics.getAuthErrors(), statistics.getIoErrors(),
                Collections.unmodifiableMap(new LinkedHashMap<>(statistics.getResponseCodeDistribution())),
                Collections.unmodifiableMap(new LinkedHashMap<>(statistics.getTopFailingPaths(10))),
                qualityGatePassed, qualityGateDescription);
    }
}
