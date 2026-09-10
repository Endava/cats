package com.endava.cats.report;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ExecutionSummary;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@QuarkusTest
class TestCaseExporterJunitTest {

    @TempDir
    Path reportDirectory;

    @Test
    void shouldExposeTheTypedSummaryContextToJunitTemplates() throws Exception {
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        ProcessingArguments processingArguments = Mockito.mock(ProcessingArguments.class);
        StopArguments stopArguments = Mockito.mock(StopArguments.class);
        Mockito.when(reportingArguments.getOutputReportFolder()).thenReturn(reportDirectory.toString());
        Mockito.when(reportingArguments.getMaskedHeaders()).thenReturn(Set.of());
        ExecutionStatisticsListener statistics = new ExecutionStatisticsListener();
        statistics.increaseSuccess("/customers");
        ExecutionSummary executionSummary = statistics.snapshot(true, "Default: fail on any error");
        TestCaseExporterJunit exporter = new TestCaseExporterJunit(reportingArguments, new CatsGlobalContext(),
                processingArguments, stopArguments);
        exporter.appVersion = "test-version";
        exporter.initPath(reportDirectory.toString());

        exporter.writeSummary(List.of(summary()), executionSummary);

        Assertions.assertThat(Files.readString(reportDirectory.resolve("junit.xml")))
                .contains("name=\"CATS test-version\"", "tests=\"1\"", "HappyPathFuzzer")
                .containsPattern("timestamp=\"[^\"]+\"");
    }

    private static CatsTestCaseSummary summary() {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setTestId("Test 1");
        testCase.setScenario("A report scenario");
        testCase.setResult("success");
        testCase.setResultReason("Response matches expectations");
        testCase.setResultDetails("");
        testCase.setFuzzer("HappyPathFuzzer");
        testCase.setContractPath("/customers");
        testCase.setRequest(CatsRequest.builder().httpMethod("POST").build());
        testCase.setResponse(CatsResponse.builder().responseCode(201).responseTimeInMs(12).body("{}").build());
        return CatsTestCaseSummary.fromCatsTestCase(testCase);
    }
}
