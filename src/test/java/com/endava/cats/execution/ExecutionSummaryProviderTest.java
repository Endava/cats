package com.endava.cats.execution;

import com.endava.cats.args.QualityGateArguments;
import com.endava.cats.report.ExecutionStatisticsListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class ExecutionSummaryProviderTest {

    @Test
    void shouldCaptureStatisticsAndQualityGateInOneSnapshot() {
        ExecutionStatisticsListener statistics = new ExecutionStatisticsListener();
        QualityGateArguments qualityGate = Mockito.mock(QualityGateArguments.class);
        statistics.increaseErrors("/orders");
        Mockito.when(qualityGate.shouldFailBuild(1, 0)).thenReturn(true);
        Mockito.when(qualityGate.getQualityGateDescription()).thenReturn("fail on errors");

        var result = new ExecutionSummaryProvider(statistics, qualityGate).snapshot();

        Assertions.assertThat(result.errors()).isOne();
        Assertions.assertThat(result.qualityGatePassed()).isFalse();
        Assertions.assertThat(result.qualityGateDescription()).isEqualTo("fail on errors");
        Mockito.verify(qualityGate).shouldFailBuild(1, 0);
    }
}
