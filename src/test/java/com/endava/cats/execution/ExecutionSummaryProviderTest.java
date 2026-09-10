package com.endava.cats.execution;

import com.endava.cats.args.QualityGateArguments;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.RunOutcome;
import com.endava.cats.report.ExecutionStatisticsListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

@QuarkusTest
class ExecutionSummaryProviderTest {

    @Test
    void shouldCaptureStatisticsAndQualityGateInOneSnapshot() {
        ExecutionStatisticsListener statistics = Mockito.mock(ExecutionStatisticsListener.class);
        QualityGateArguments qualityGate = Mockito.mock(QualityGateArguments.class);
        ExecutionSummary expected = new ExecutionSummary(5, 4, 3, 0, 1, 0, 1, 0, 0,
                Map.of(200, 3), Map.of("/orders", 1L), false, "fail on errors", RunOutcome.completed());
        Mockito.when(statistics.getErrors()).thenReturn(1L);
        Mockito.when(statistics.getWarns()).thenReturn(0L);
        Mockito.when(qualityGate.shouldFailBuild(1, 0)).thenReturn(true);
        Mockito.when(qualityGate.getQualityGateDescription()).thenReturn("fail on errors");
        Mockito.when(statistics.snapshot(false, "fail on errors")).thenReturn(expected);

        ExecutionSummary result = new ExecutionSummaryProvider(statistics, qualityGate).snapshot();

        Assertions.assertThat(result).isSameAs(expected);
        Mockito.verify(statistics).snapshot(false, "fail on errors");
    }
}
