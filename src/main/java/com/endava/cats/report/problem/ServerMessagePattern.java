package com.endava.cats.report.problem;

import com.endava.cats.model.Problem;
import com.endava.cats.model.ProblemKind;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Whitespace-token pattern compatibility for server-message identities that Drain generalized differently. */
public final class ServerMessagePattern {
    private ServerMessagePattern() {
    }

    public static boolean matches(String baseline, String target) {
        if (baseline == null || target == null || baseline.isBlank() || target.isBlank()) {
            return false;
        }
        String[] left = baseline.strip().split("\\s+", -1);
        String[] right = target.strip().split("\\s+", -1);
        return left.length == right.length && IntStream.range(0, left.length)
                .allMatch(index -> left[index].equals(right[index]) || "<*>".equals(left[index]) || "<*>".equals(right[index]));
    }

    public static boolean matches(Problem baseline, Problem target) {
        return baseline.kind().isIdentifiedByServerMessage() && baseline.kind() == target.kind()
                && (baseline.kind() != ProblemKind.ERROR_MESSAGE || families(baseline).equals(families(target)))
                && matches(baseline.errorTemplate(), target.errorTemplate());
    }

    private static Set<String> families(Problem problem) {
        return problem.reasons().stream().map(ResultReasons::verdictFamily).collect(Collectors.toSet());
    }
}
