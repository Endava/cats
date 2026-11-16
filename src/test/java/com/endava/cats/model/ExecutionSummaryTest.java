package com.endava.cats.model;

import com.endava.cats.report.ExecutionStatisticsListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@QuarkusTest
class ExecutionSummaryTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0 | 60 | 120 | ''",
            "-1 | 60 | 120 | ''",
            "5 | 60 | 120 | 5 requests throttled (2 retried successfully) on 3 operations; effective rate lowered to 60 rpm",
            "5 | 60 | 60 | 5 requests throttled (2 retried successfully) on 3 operations; effective rate 60 rpm",
            "5 | 120 | 60 | 5 requests throttled (2 retried successfully) on 3 operations; effective rate 120 rpm",
            "5 | 60 | 0 | 5 requests throttled (2 retried successfully) on 3 operations; effective rate 60 rpm",
            "5 | 0 | 120 | 5 requests throttled (2 retried successfully) on 3 operations"
    })
    void shouldDescribeThrottlingForReducedUnchangedAndUnknownRates(long throttled, int effectiveRpm, int configuredRpm,
                                                                  String expected) {
        ExecutionSummary summary = throttlingSummary(throttled, effectiveRpm, configuredRpm, List.of());

        Assertions.assertThat(summary.throttlingSummary()).isEqualTo(expected);
        Assertions.assertThat(summary.rateLimitedOperationsHint()).isEmpty();
    }

    @Test
    void shouldCopyRateLimitedOperationsAndDescribeThemInOrder() {
        List<String> operations = new ArrayList<>(List.of("GET /pets", "POST /orders"));
        ExecutionSummary summary = throttlingSummary(5, 60, 120, operations);
        operations.clear();

        Assertions.assertThat(summary.rateLimitedOperations()).containsExactly("GET /pets", "POST /orders");
        Assertions.assertThat(summary.rateLimitedOperationsHint()).isEqualTo(
                "More than 50% of final responses on GET /pets, POST /orders were HTTP 429 Too Many Requests; use --maxRequestsPerMinute to lower the request rate.");
        Assertions.assertThatThrownBy(() -> summary.rateLimitedOperations().add("DELETE /pets"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldNormalizeMissingRateLimitedOperationsToAnEmptyList() {
        ExecutionSummary summary = throttlingSummary(0, 0, 0, null);

        Assertions.assertThat(summary.rateLimitedOperations()).isEmpty();
        Assertions.assertThat(summary.rateLimitedOperationsHint()).isEmpty();
    }

    private static ExecutionSummary throttlingSummary(long throttled, int effectiveRpm, int configuredRpm,
                                                      List<String> operations) {
        return new ExecutionSummary(10, 10, 5, 2, 3, 0, 0, 0, Map.of(), Map.of(), false, "errors",
                RunOutcome.completed(), throttled, 2, 3, effectiveRpm, configuredRpm, operations);
    }

    @Test
    void shouldCreateAnImmutableSnapshotOfExecutionStatistics() {
        ExecutionStatisticsListener statistics = new ExecutionStatisticsListener();
        statistics.increaseSuccess("/pets");
        statistics.increaseWarns("/orders");
        statistics.increaseErrors("/pets");
        statistics.increaseSkippedFromReporting("/hidden");
        for (int i = 0; i < 4; i++) {
            statistics.increaseRequestsAttempted();
        }
        statistics.increaseAuthErrors();
        statistics.increaseIoErrors();
        statistics.recordResponseCode(200);
        statistics.recordResponseCode(500);

        ExecutionSummary snapshot = statistics.snapshot(false, "fail on errors");
        Map<Integer, Integer> responseCodeDistribution = snapshot.responseCodeDistribution();
        Map<String, Long> topFailingPaths = snapshot.topFailingPaths();

        Assertions.assertThat(snapshot.requestsAttempted()).isEqualTo(4);
        Assertions.assertThat(snapshot.reportedResults()).isEqualTo(3);
        Assertions.assertThat(snapshot.success()).isEqualTo(1);
        Assertions.assertThat(snapshot.warnings()).isEqualTo(1);
        Assertions.assertThat(snapshot.errors()).isEqualTo(1);
        Assertions.assertThat(snapshot.skippedFromReporting()).isEqualTo(1);
        Assertions.assertThat(snapshot.authenticationErrors()).isEqualTo(1);
        Assertions.assertThat(snapshot.ioErrors()).isEqualTo(1);
        Assertions.assertThat(responseCodeDistribution).containsEntry(200, 1).containsEntry(500, 1);
        Assertions.assertThat(topFailingPaths).containsEntry("/pets", 1L);
        Assertions.assertThat(snapshot.qualityGatePassed()).isFalse();
        Assertions.assertThat(snapshot.qualityGateDescription()).isEqualTo("fail on errors");
        Assertions.assertThat(snapshot.outcome()).isEqualTo(RunOutcome.completed());
        Assertions.assertThatThrownBy(() -> responseCodeDistribution.put(201, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(() -> topFailingPaths.put("/new", 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
