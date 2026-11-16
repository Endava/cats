package com.endava.cats.report;

import com.endava.cats.command.model.ComparisonResult;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.Problem;
import com.endava.cats.model.ProblemKind;
import com.endava.cats.report.problem.OccurrenceDelta.MatchedBy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.endava.cats.report.ComparisonFixtures.occurrence;
import static com.endava.cats.report.ComparisonFixtures.problem;
import static com.endava.cats.report.ComparisonFixtures.report;

@io.quarkus.test.junit.QuarkusTest
class BaselineComparisonEngineTest {
    @Test
    void shouldShowCountChangesWithoutRegressing() {
        var base = problem("a", ProblemKind.STATUS_CODE, 2);
        var result = compare(report(base), report(problem("a", ProblemKind.STATUS_CODE, 7)), false);
        Assertions.assertThat(result.regression()).isFalse();
        Assertions.assertThat(result.unchangedOccurrences()).singleElement().satisfies(delta -> {
            Assertions.assertThat(delta.baselineTests()).isEqualTo(2);
            Assertions.assertThat(delta.targetTests()).isEqualTo(7);
            Assertions.assertThat(delta.matchedBy()).isEqualTo(MatchedBy.ID);
        });
    }

    @Test
    void shouldDetectBugSwapWithEqualCounts() {
        var result = compare(report(problem("a", ProblemKind.STATUS_CODE, 2)), report(problem("b", ProblemKind.STATUS_CODE, 2)), false);
        Assertions.assertThat(result.newOccurrences()).hasSize(1);
        Assertions.assertThat(result.fixedOccurrences()).hasSize(1);
        Assertions.assertThat(result.regression()).isTrue();
    }

    @Test
    void shouldCompareAcceptedRequestsPerFieldAndUseFieldCounts() {
        Problem base = accepted(List.of(new Problem.FieldCount("body.name", 2)));
        Problem target = accepted(List.of(new Problem.FieldCount("body.name", 1), new Problem.FieldCount("body.note", 3)));
        var result = compare(report(base), report(target), false);
        Assertions.assertThat(result.newOccurrences()).singleElement().satisfies(delta -> {
            Assertions.assertThat(delta.field()).isEqualTo("body.note");
            Assertions.assertThat(delta.targetTests()).isEqualTo(3);
        });
        Assertions.assertThat(result.unchangedOccurrences()).singleElement().satisfies(delta -> {
            Assertions.assertThat(delta.field()).isEqualTo("body.name");
            Assertions.assertThat(delta.baselineTests()).isEqualTo(2);
            Assertions.assertThat(delta.targetTests()).isEqualTo(1);
        });
        Assertions.assertThat(compare(report(target), report(base), false).fixedOccurrences()).hasSize(1);
    }

    @Test
    void shouldKeepAnEmptyFieldWhenThereAreNoMutationTargets() {
        var result = compare(report(), report(accepted(List.of())), false);
        Assertions.assertThat(result.newOccurrences()).singleElement().satisfies(delta -> Assertions.assertThat(delta.field()).isEmpty());
    }

    @Test
    void shouldMatchGeneralizedMessagesInEitherDirection() {
        Problem base = problem("a", ProblemKind.SERVER_ERROR, 2);
        Problem target = generalized("b", ProblemKind.SERVER_ERROR);
        Assertions.assertThat(compare(report(base), report(target), false).unchangedOccurrences())
                .singleElement().satisfies(delta -> Assertions.assertThat(delta.matchedBy()).isEqualTo(MatchedBy.PATTERN));
        Assertions.assertThat(compare(report(target), report(base), false).matchedByPattern()).isEqualTo(1);
    }

    @Test
    void shouldConsumePatternsOnlyOnceInPersistedRankOrderAndPreferExactIds() {
        Problem base = problem("a", ProblemKind.SERVER_ERROR, 1);
        Problem generalized = generalized("b", ProblemKind.SERVER_ERROR);
        var result = compare(report(base), report(generalized, generalized("c", ProblemKind.SERVER_ERROR)), false);
        Assertions.assertThat(result.unchangedOccurrences()).extracting("problemId").containsExactly("b");
        Assertions.assertThat(result.newOccurrences()).extracting("problemId").containsExactly("c");
        var exact = compare(report(base), report(generalized, base), false);
        Assertions.assertThat(exact.matchedByPattern()).isZero();
        Assertions.assertThat(exact.unchangedOccurrences()).extracting("matchedBy").containsExactly(MatchedBy.ID);
    }

    @Test
    void shouldRestrictPatternsToKindOperationStatusAndVerdictFamily() {
        Problem base = problem("a", ProblemKind.ERROR_MESSAGE, 1);
        Problem target = generalized("b", ProblemKind.ERROR_MESSAGE);
        Assertions.assertThat(compare(report(base), report(target), false).matchedByPattern()).isEqualTo(1);
        List<Problem> incompatible = List.of(
                target.toBuilder().kind(ProblemKind.SERVER_ERROR).build(),
                target.toBuilder().reasons(List.of("Undocumented response code: 400")).build(),
                target.toBuilder().occurrences(List.of(occurrence("GET /orders", 1, List.of()))).build(),
                target.toBuilder().occurrences(List.of(new Problem.Occurrence("POST /orders", List.of(), List.of("Test 1"), List.of(422), List.of("RemoveFields")))).build());
        incompatible.forEach(other -> Assertions.assertThat(compare(report(base), report(other), false).matchedByPattern()).isZero());
        Assertions.assertThat(compare(report(problem("a", ProblemKind.STATUS_CODE, 1)),
                report(generalized("b", ProblemKind.STATUS_CODE)), false).matchedByPattern()).isZero();
    }

    @Test
    void shouldMergeGenericVerdictFamiliesForMessageMatching() {
        Problem base = problem("a", ProblemKind.ERROR_MESSAGE, 1);
        Problem target = generalized("b", ProblemKind.ERROR_MESSAGE).toBuilder().reasons(List.of("Unexpected behaviour 400")).build();
        Assertions.assertThat(compare(report(base), report(target), false).matchedByPattern()).isEqualTo(1);
    }

    @Test
    void shouldReportAbsentFuzzersAsNotComparableInBothDirections() {
        CatsTestReport absent = CatsTestReport.builder().problemKeyVersion(1).problems(List.of())
                .testCases(List.of(ComparisonFixtures.ran("HappyPath"))).build();
        Problem found = problem("a", ProblemKind.SERVER_ERROR, 1);
        Assertions.assertThat(compare(absent, report(found), false).notComparable()).hasSize(1);
        Assertions.assertThat(compare(report(found), absent, false).notComparable()).hasSize(1);
        Assertions.assertThat(compare(absent, report(found), true).regression()).isFalse();
    }

    @Test
    void shouldCountSuccessfulTestsAsRanAndRequireAllOccurrenceFuzzersToBeAbsent() {
        Problem found = problem("a", ProblemKind.SERVER_ERROR, 1).toBuilder().occurrences(List.of(
                new Problem.Occurrence("POST /orders", List.of(), List.of("Test 1"), List.of(500), List.of("Absent", "RemoveFields")))).build();
        Assertions.assertThat(compare(report(), report(found), false).newOccurrences()).hasSize(1);
        Assertions.assertThat(compare(report(found), report(), false).fixedOccurrences()).hasSize(1);
    }

    @Test
    void shouldGateWarningsOnlyWhenRequested() {
        var target = report(problem("a", ProblemKind.STATUS_CODE, 1).toBuilder().level("warn").build());
        Assertions.assertThat(compare(report(), target, false).regression()).isFalse();
        Assertions.assertThat(compare(report(), target, true).regression()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, -1, 2})
    void shouldRejectOtherVersionsOnEitherSide(int version) {
        var old = CatsTestReport.builder().problemKeyVersion(version).problems(List.of()).testCases(List.of()).build();
        Assertions.assertThatThrownBy(() -> compare(old, report(), false))
                .isInstanceOf(com.endava.cats.exception.UnsupportedReportException.class)
                .hasMessageContaining("baseline was written by a CATS version without problems")
                .hasMessageContaining("re-run it with this CATS version");
        Assertions.assertThatThrownBy(() -> compare(report(), old, false))
                .isInstanceOf(com.endava.cats.exception.UnsupportedReportException.class).hasMessageContaining("target was written");
    }

    @Test
    void shouldRejectMissingVersionWithoutRecomputing(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var file = directory.resolve("old.json");
        java.nio.file.Files.writeString(file, "{\"testCases\":[],\"problems\":[]}");
        Assertions.assertThatThrownBy(() -> BaselineComparisonEngine.loadReport(file))
                .isInstanceOf(com.endava.cats.exception.UnsupportedReportException.class)
                .hasMessageContaining("problemKeyVersion 0, expected 1");
    }

    private static Problem accepted(List<Problem.FieldCount> fields) {
        return problem("a", ProblemKind.ACCEPTED_REQUEST, 4).toBuilder()
                .occurrences(List.of(occurrence("POST /orders", 4, fields))).build();
    }

    private static Problem generalized(String id, ProblemKind kind) {
        return problem(id, kind, 1).toBuilder().errorTemplate("Cluster <*> cannot be found.").build();
    }

    private static ComparisonResult compare(CatsTestReport baseline, CatsTestReport target, boolean warnings) {
        return BaselineComparisonEngine.compare(new BaselineComparisonEngine.Reports("baseline", "target", baseline, target), warnings);
    }
}
