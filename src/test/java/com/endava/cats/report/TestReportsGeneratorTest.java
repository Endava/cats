package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.RunOutcome;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@QuarkusTest
class TestReportsGeneratorTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldShareOneExecutionSummaryWithEveryReportConsumer() {
        TestCaseExporter htmlExporter = Mockito.mock(TestCaseExporter.class);
        TestCaseExporter junitExporter = Mockito.mock(TestCaseExporter.class);
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        ExecutionSummary summary = new ExecutionSummary(5, 5, 4, 3, 0, 1, 1, 1, 0, 0,
                Map.of(200, 3), Map.of("/orders", 1L), false, "fail on errors", RunOutcome.completed());

        Mockito.when(exporters.stream()).thenReturn(Stream.of(htmlExporter, junitExporter));
        Mockito.when(reportingArguments.getReportFormat()).thenReturn(List.of(
                ReportingArguments.ReportFormat.HTML_ONLY, ReportingArguments.ReportFormat.JUNIT));
        Mockito.when(htmlExporter.reportFormat()).thenReturn(ReportingArguments.ReportFormat.HTML_ONLY);
        Mockito.when(junitExporter.reportFormat()).thenReturn(ReportingArguments.ReportFormat.JUNIT);
        TestReportsGenerator generator = new TestReportsGenerator(exporters, reportingArguments);

        generator.writeSummary(List.of(), summary);
        generator.printExecutionDetails(summary);

        Mockito.verify(htmlExporter).writeSummary(List.of(), summary);
        Mockito.verify(junitExporter).writeSummary(List.of(), summary);
        Mockito.verify(htmlExporter).printExecutionDetails(summary);
        Mockito.verify(junitExporter, Mockito.never()).printExecutionDetails(Mockito.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldChoosePrimaryExporterByRequestedFormatRatherThanCdiOrder() {
        TestCaseExporter junitExporter = Mockito.mock(TestCaseExporter.class);
        TestCaseExporter htmlExporter = Mockito.mock(TestCaseExporter.class);
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        CatsTestCase testCase = new CatsTestCase();

        Mockito.when(exporters.stream()).thenReturn(Stream.of(junitExporter, htmlExporter));
        Mockito.when(reportingArguments.getReportFormat()).thenReturn(List.of(
                ReportingArguments.ReportFormat.HTML_ONLY, ReportingArguments.ReportFormat.JUNIT));
        Mockito.when(htmlExporter.reportFormat()).thenReturn(ReportingArguments.ReportFormat.HTML_ONLY);
        Mockito.when(junitExporter.reportFormat()).thenReturn(ReportingArguments.ReportFormat.JUNIT);

        TestReportsGenerator generator = new TestReportsGenerator(exporters, reportingArguments);
        generator.writeTestCase(testCase);

        Mockito.verify(htmlExporter).writeTestCase(testCase);
        Mockito.verify(junitExporter, Mockito.never()).writeTestCase(Mockito.any());
    }
}
