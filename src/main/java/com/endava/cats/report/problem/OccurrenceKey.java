package com.endava.cats.report.problem;

/** The persisted problem on an operation; accepted requests additionally identify the mutated field. */
public record OccurrenceKey(String problemId, String operation, String field) {
}
