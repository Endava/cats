package com.endava.cats.tui.model;

import com.endava.cats.report.ExecutionStatisticsListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RunSummarySnapshotTest {

    @Test
    void shouldCreateAnImmutableSnapshotOfExecutionStatistics() {
        ExecutionStatisticsListener statistics = new ExecutionStatisticsListener();
        statistics.increaseSuccess("/pets");
        statistics.increaseWarns("/orders");
        statistics.increaseErrors("/pets");
        statistics.increaseSkipped();
        statistics.increaseSkippedFromReporting("/hidden");
        statistics.increaseAuthErrors();
        statistics.increaseIoErrors();
        statistics.recordResponseCode(200);
        statistics.recordResponseCode(500);

        RunSummarySnapshot snapshot = RunSummarySnapshot.from(statistics, false, "fail on errors");

        Assertions.assertThat(snapshot.totalRequests()).isEqualTo(4);
        Assertions.assertThat(snapshot.reportedResults()).isEqualTo(3);
        Assertions.assertThat(snapshot.success()).isEqualTo(1);
        Assertions.assertThat(snapshot.warnings()).isEqualTo(1);
        Assertions.assertThat(snapshot.errors()).isEqualTo(1);
        Assertions.assertThat(snapshot.skipped()).isEqualTo(1);
        Assertions.assertThat(snapshot.skippedFromReporting()).isEqualTo(1);
        Assertions.assertThat(snapshot.authenticationErrors()).isEqualTo(1);
        Assertions.assertThat(snapshot.ioErrors()).isEqualTo(1);
        Assertions.assertThat(snapshot.responseCodeDistribution()).containsEntry(200, 1).containsEntry(500, 1);
        Assertions.assertThat(snapshot.topFailingPaths()).containsEntry("/pets", 1L);
        Assertions.assertThat(snapshot.qualityGatePassed()).isFalse();
        Assertions.assertThat(snapshot.qualityGateDescription()).isEqualTo("fail on errors");
        Assertions.assertThatThrownBy(() -> snapshot.responseCodeDistribution().put(201, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(() -> snapshot.topFailingPaths().put("/new", 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
