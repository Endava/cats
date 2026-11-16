package com.endava.cats.report.problem;

import com.endava.cats.model.ProblemKind;

import java.util.List;

/** One occurrence in the baseline difference. Counts are information, never identity. */
public record OccurrenceDelta(State state, String problemId, ProblemKind kind, String level, String title,
                              String operation, String field, List<Integer> statuses,
                              int baselineTests, int targetTests, MatchedBy matchedBy) {
    public enum State { NEW, FIXED, UNCHANGED, NOT_COMPARABLE }

    public enum MatchedBy { ID, PATTERN }

    public boolean isError() {
        return "error".equals(level);
    }
}
