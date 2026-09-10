package com.endava.cats.execution;

import com.endava.cats.args.QualityGateArguments;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.report.ExecutionStatisticsListener;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Captures the authoritative immutable summary of the current execution. */
@ApplicationScoped
public class ExecutionSummaryProvider {
    private final ExecutionStatisticsListener executionStatisticsListener;
    private final QualityGateArguments qualityGateArguments;

    /**
     * Creates the summary provider.
     *
     * @param executionStatisticsListener current execution statistics
     * @param qualityGateArguments configured quality gate
     */
    @Inject
    public ExecutionSummaryProvider(ExecutionStatisticsListener executionStatisticsListener,
                                    QualityGateArguments qualityGateArguments) {
        this.executionStatisticsListener = executionStatisticsListener;
        this.qualityGateArguments = qualityGateArguments;
    }

    /**
     * Captures statistics, quality-gate status, and run outcome at this point in time.
     *
     * @return immutable execution summary
     */
    public ExecutionSummary snapshot() {
        return executionStatisticsListener.snapshot(
                qualityGateArguments::shouldFailBuild, qualityGateArguments.getQualityGateDescription());
    }
}
