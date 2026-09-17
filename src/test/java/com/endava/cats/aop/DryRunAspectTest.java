package com.endava.cats.aop;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.execution.ExecutionSummaryProvider;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.RunOutcome;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.interceptor.InvocationContext;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

@QuarkusTest
class DryRunAspectTest {

    @Test
    void shouldReturnTypeCompatiblePrimitiveDefaults() {
        DryRunAspect aspect = new DryRunAspect();

        Object longDefault = ReflectionTestUtils.invokeMethod(aspect, "defaultValue", long.class);
        Object intDefault = ReflectionTestUtils.invokeMethod(aspect, "defaultValue", int.class);
        Object voidDefault = ReflectionTestUtils.invokeMethod(aspect, "defaultValue", void.class);
        Assertions.assertThat(longDefault).isEqualTo(0L);
        Assertions.assertThat(intDefault).isEqualTo(0);
        Assertions.assertThat(voidDefault).isNull();
    }

    @Test
    void shouldExposeDryRunStateForTheAuthScriptParser() throws Exception {
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        ExecutionSummaryProvider summaryProvider = Mockito.mock(ExecutionSummaryProvider.class);
        DryRunAspect aspect = new DryRunAspect();
        ReflectionTestUtils.setField(aspect, "reportingArguments", reportingArguments);
        ReflectionTestUtils.setField(aspect, "executionSummaryProvider", summaryProvider);

        aspect.startSession(Mockito.mock(InvocationContext.class));
        try {
            Assertions.assertThat(DryRunAspect.isDryRun()).isTrue();
        } finally {
            aspect.endSession();
        }
        Assertions.assertThat(DryRunAspect.isDryRun()).isFalse();
    }

    @Test
    void shouldReturnTheSharedExecutionSummaryWithoutWritingReports() {
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        ExecutionSummaryProvider summaryProvider = Mockito.mock(ExecutionSummaryProvider.class);
        ExecutionSummary summary = new ExecutionSummary(0, 0, 0, 0, 0, 0, 0, 0,
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
