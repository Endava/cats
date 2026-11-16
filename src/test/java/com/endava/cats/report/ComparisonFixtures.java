package com.endava.cats.report;

import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.Problem;
import com.endava.cats.model.ProblemKind;

import java.util.List;
import java.util.stream.IntStream;

/** Synthetic persisted reports; no private response bodies. */
public final class ComparisonFixtures {
    private ComparisonFixtures() {
    }

    public static Problem problem(String id, ProblemKind kind, int tests) {
        return Problem.builder().id(id).kind(kind).level("error").title("Problem " + id)
                .reasons(List.of("Unexpected response code: 400")).errorTemplate("Cluster c1 cannot be found.")
                .testCount(tests).occurrences(List.of(occurrence("POST /orders", tests, List.of()))).build();
    }

    public static Problem.Occurrence occurrence(String operation, int tests, List<Problem.FieldCount> fields) {
        return new Problem.Occurrence(operation, fields, IntStream.range(0, tests).mapToObj(i -> "Test " + i).toList(),
                List.of(400), List.of("RemoveFields"));
    }

    public static CatsTestReport report(Problem... problems) {
        return CatsTestReport.builder().problemKeyVersion(Problem.KEY_VERSION).problems(List.of(problems))
                .testCases(List.of(ran("RemoveFields"))).build();
    }

    public static CatsTestCaseSummary ran(String fuzzer) {
        return com.endava.cats.util.JsonUtils.GSON.fromJson("{\"fuzzer\":\"" + fuzzer + "\",\"result\":\"success\"}", CatsTestCaseSummary.class);
    }
}
