package com.endava.cats.aop;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.execution.ExecutionSummaryProvider;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.RunOutcome;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

@QuarkusTest
class DryRunAspectTest {

    @Test
    void shouldReturnTheSharedExecutionSummaryWithoutWritingReports() {
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        ExecutionSummaryProvider summaryProvider = Mockito.mock(ExecutionSummaryProvider.class);
        ExecutionSummary summary = new ExecutionSummary(0, 0, 0, 0, 0, 0, 0, 0, 0,
                Map.of(), Map.of(), true, "", RunOutcome.completed());
        DryRunAspect aspect = new DryRunAspect();
        ReflectionTestUtils.setField(aspect, "reportingArguments", reportingArguments);
        ReflectionTestUtils.setField(aspect, "executionSummaryProvider", summaryProvider);
        Mockito.when(reportingArguments.isJsonOutput()).thenReturn(true);
        Mockito.when(summaryProvider.snapshot()).thenReturn(summary);

        Assertions.assertThat(aspect.endSession()).isSameAs(summary);
        Mockito.verify(summaryProvider).snapshot();
    }
}
