package com.endava.cats.aop;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.execution.ExecutionSummaryProvider;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RunOutcome;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.interceptor.InvocationContext;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
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

    @Test
    void shouldReturnBooleanFalseForNonVoidReportGuardsWithoutCountingTest() throws Exception {
        DryRunAspect aspect = new DryRunAspect();
        InvocationContext context = Mockito.mock(InvocationContext.class);
        Method rateLimitMethod = TestCaseListener.class.getMethod(
                "reportRateLimitedResponse", PrettyLogger.class, FuzzingData.class, CatsResponse.class);
        FuzzingData data = FuzzingData.builder().path("/items").method(HttpMethod.POST).build();
        Mockito.when(context.getMethod()).thenReturn(rateLimitMethod);
        Mockito.when(context.getParameters()).thenReturn(new Object[]{null, data, CatsResponse.empty()});

        Object result = aspect.report(context);

        Assertions.assertThat(result).isEqualTo(false);
        Map<?, ?> paths = (Map<?, ?>) ReflectionTestUtils.getField(aspect, "paths");
        Assertions.assertThat(paths).isEmpty();
    }
}
