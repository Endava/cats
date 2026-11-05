package com.endava.cats.command.triage;

import java.util.List;

/**
 * Complete triage outcome: root-cause clusters plus the synthesized {@code --correlations}, mined {@code --refData}
 * and OpenAPI contract-patch artifacts.
 */
public record TriageReport(String reportFolder,
                           int totalTestsScanned,
                           int totalErrors,
                           int totalWarnings,
                           int totalClusters,
                           double noiseReductionPercent,
                           List<RootCauseCluster> clusters,
                           List<SynthesizedCorrelationRule> synthesizedCorrelations,
                           String synthesizedCorrelationsYaml,
                           String minedRefDataYaml,
                           String contractPatchYaml) {
}
