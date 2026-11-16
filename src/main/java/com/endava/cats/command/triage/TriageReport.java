package com.endava.cats.command.triage;

import java.util.List;

/**
 * Complete triage outcome: the report's problems with each occurrence triaged, plus the synthesized
 * {@code --correlations}, mined {@code --refData} and OpenAPI contract-patch artifacts.
 *
 * @param reportFolder              the triaged report folder
 * @param problemKeyVersion         the problem key version of the report
 * @param totalTestsScanned         the number of {@code TestN.json} files read
 * @param totalErrors               the number of error tests
 * @param totalWarnings             the number of warning tests
 * @param totalProblems             the number of triaged problems
 * @param totalOccurrences          the number of triaged occurrences
 * @param noiseReductionPercent     how many fewer problems than triaged tests, in percent
 * @param problems                  the triaged problems, in report rank order
 * @param synthesizedCorrelations   the synthesized {@code --correlations} rules
 * @param synthesizedCorrelationsYaml the rules as YAML
 * @param minedRefDataYaml          the mined {@code --refData} as YAML
 * @param contractPatchYaml         the contract patch for undocumented status codes
 */
public record TriageReport(String reportFolder,
                           int problemKeyVersion,
                           int totalTestsScanned,
                           int totalErrors,
                           int totalWarnings,
                           int totalProblems,
                           int totalOccurrences,
                           double noiseReductionPercent,
                           List<TriageProblem> problems,
                           List<SynthesizedCorrelationRule> synthesizedCorrelations,
                           String synthesizedCorrelationsYaml,
                           String minedRefDataYaml,
                           String contractPatchYaml) {
}
