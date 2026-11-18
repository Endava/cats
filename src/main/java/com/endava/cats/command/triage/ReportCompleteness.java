package com.endava.cats.command.triage;

import com.endava.cats.exception.UnsupportedReportException;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.Problem;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Prevents partial or unreadable saved evidence from being interpreted as a clean report. */
final class ReportCompleteness {
    private ReportCompleteness() {
    }

    static void validate(CatsTestReport summary, List<TriageTestCase> tests) {
        Set<String> loaded = tests.stream().map(test -> test.testCase().getTestId()).collect(Collectors.toSet());
        List<String> missing = referencedIds(summary).filter(StringUtils::isNotBlank)
                .distinct().filter(id -> !loaded.contains(id)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new UnsupportedReportException("Incomplete report: %d referenced test file(s) are missing, unreadable or lack request/response data: %s%s. "
                    .formatted(missing.size(), String.join(", ", missing.stream().limit(10).map(id -> normalized(id) + ".json").toList()), missing.size() > 10 ? ", ..." : "")
                    + "Copy the complete report including Test*.json, or re-run CATS. No triage or exports were produced.");
        }
    }

    private static Stream<String> referencedIds(CatsTestReport summary) {
        Stream<String> tests = Optional.ofNullable(summary.getTestCases()).orElse(List.of()).stream().map(CatsTestCaseSummary::getId);
        Stream<String> problems = summary.getProblems().stream().flatMap(problem -> problem.occurrences().stream())
                .map(Problem.Occurrence::testIds).flatMap(List::stream);
        return Stream.concat(tests, problems);
    }

    private static String normalized(String id) {
        return StringUtils.deleteWhitespace(StringUtils.defaultString(id));
    }
}
