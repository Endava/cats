package com.endava.cats.command.model;

import com.endava.cats.report.problem.OccurrenceDelta;

import java.util.List;

/** The occurrence difference, shared by standalone comparison and the run-time baseline gate. */
public record ComparisonResult(String baseline, String target, int problemKeyVersion, boolean regression,
                               List<OccurrenceDelta> newOccurrences, List<OccurrenceDelta> fixedOccurrences,
                               List<OccurrenceDelta> unchangedOccurrences, List<OccurrenceDelta> notComparable,
                               int matchedByPattern) {
}
