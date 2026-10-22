package com.endava.cats.report;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.RunOutcome;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.webfuzzing.commons.VersionNumbers;
import com.webfuzzing.commons.faults.DefinedFaultCategory;
import com.webfuzzing.commons.report.Report;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@QuarkusTest
class TestCaseExporterWfcReportTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldGenerateValidWfcReportAndSupportMultiFormatExecutionWithHtmlJs() throws Exception {
        ReportingArguments reportingArguments = new ReportingArguments();
        ProcessingArguments processingArguments = Mockito.mock(ProcessingArguments.class);
        StopArguments stopArguments = Mockito.mock(StopArguments.class);
        CatsGlobalContext globalContext = new CatsGlobalContext();
        globalContext.setOpenAPI(OpenApiUtils.readOpenApi("src/test/resources/petstore.yml"));
        globalContext.recordError("Sample schema warning");

        TestCaseExporterHtmlJs htmlExporter = new TestCaseExporterHtmlJs(
                reportingArguments, globalContext, processingArguments, stopArguments);
        htmlExporter.appVersion = "15.0.4";
        TestCaseExporterWfcReport wfcExporter = new TestCaseExporterWfcReport(
                reportingArguments, globalContext, processingArguments, stopArguments);
        wfcExporter.appVersion = "15.0.4";

        @SuppressWarnings("unchecked")
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        Mockito.when(exporters.stream()).thenAnswer(_ -> Stream.of(htmlExporter, wfcExporter));

        ReportingArguments spyReportingArgs = Mockito.spy(reportingArguments);
        Mockito.when(spyReportingArgs.getReportFormat()).thenReturn(List.of(
                ReportingArguments.ReportFormat.HTML_JS,
                ReportingArguments.ReportFormat.WFC_REPORT));

        TestReportsGenerator generator = new TestReportsGenerator(exporters, spyReportingArgs);
        generator.initPath(tempDir.toString());
        generator.writeHelperFiles();

        CatsTestCase tc1 = new CatsTestCase();
        tc1.setTestId("Test 1");
        tc1.setScenario("Happy path POST /pets");
        tc1.setResult("success");
        tc1.setResultReason(Reason.ALL_GOOD.value());
        tc1.setFuzzer("HappyPath");
        tc1.setPath("/pets");
        tc1.setContractPath("/pets");
        tc1.setFullRequestPath("http://localhost:8080/pets");
        tc1.setRequest(CatsRequest.builder().httpMethod("POST").url("http://localhost:8080/pets").payload("{\"name\":\"Rex\"}").build());
        tc1.setResponse(CatsResponse.builder().responseCode(200).responseTimeInMs(12).body("{\"id\":1}").build());

        CatsTestCase tc2 = new CatsTestCase();
        tc2.setTestId("Test 2");
        tc2.setScenario("SQLi on POST /pets");
        tc2.setResult("error");
        tc2.setResultReason("Unexpected response code: 500");
        tc2.setResultDetails("Server crashed with 500");
        tc2.setFuzzer("SqlInjectionInStringFields");
        tc2.setPath("/pets");
        tc2.setContractPath("/pets");
        tc2.setFullRequestPath("http://localhost:8080/pets");
        tc2.setRequest(CatsRequest.builder().httpMethod("POST").url("http://localhost:8080/pets").payload("{\"name\":\"' OR 1=1--\"}").build());
        tc2.setResponse(CatsResponse.builder().responseCode(500).responseTimeInMs(25).body("{\"error\":\"Database error\"}").build());

        CatsTestCase tc3 = new CatsTestCase();
        tc3.setTestId("Test 3");
        tc3.setScenario("Socket timeout on GET /pets/{id}");
        tc3.setResult("error");
        tc3.setResultReason(Reason.CALL_TIMEOUT_EXCEEDED.value());
        tc3.setFuzzer("HappyPath");
        tc3.setPath("/pets/1");
        tc3.setContractPath("/pets/{id}");
        tc3.setFullRequestPath("http://localhost:8080/pets/1");
        tc3.setRequest(CatsRequest.builder().httpMethod("GET").url("http://localhost:8080/pets/1").payload("{}").build());
        tc3.setResponse(CatsResponse.builder().responseCode(953).responseTimeInMs(5000).body("").build());

        generator.writeTestCase(tc1);
        generator.writeTestCase(tc2);
        generator.writeTestCase(tc3);

        List<CatsTestCaseSummary> summaries = List.of(
                CatsTestCaseSummary.fromCatsTestCase(tc1),
                CatsTestCaseSummary.fromCatsTestCase(tc2),
                CatsTestCaseSummary.fromCatsTestCase(tc3));
        ExecutionSummary execSummary = new ExecutionSummary(
                10, 3, 1, 0, 0, 2, 0, 1,
                Map.of(200, 1, 500, 1, 953, 1),
                Map.of("/pets", 1L, "/pets/{id}", 1L),
                false, "Default: fail on any error", RunOutcome.completed());

        generator.writeSummary(summaries, execSummary);

        // Both HTML_JS (index.html, Test1.html, cats-summary-report.json) and WFC_REPORT (report.json) must exist
        Assertions.assertThat(tempDir.resolve("index.html")).exists();
        Assertions.assertThat(tempDir.resolve("cats-summary-report.json")).exists();
        Assertions.assertThat(tempDir.resolve("Test1.html")).exists();
        Assertions.assertThat(tempDir.resolve("Test1.json")).exists();

        Path wfcPath = tempDir.resolve(TestCaseExporterWfcReport.WFC_REPORT_FILE);
        Assertions.assertThat(wfcPath).exists();
        String wfcJson = Files.readString(wfcPath);
        Assertions.assertThat(wfcJson).doesNotContain("additionalProperties");

        Report parsed = JsonUtils.GSON.fromJson(wfcJson, Report.class);
        Assertions.assertThat(parsed.getSchemaVersion()).isEqualTo(VersionNumbers.REPORT);
        Assertions.assertThat(parsed.getToolName()).isEqualTo("CATS");
        Assertions.assertThat(parsed.getTotalTests()).isEqualTo(3);
        Assertions.assertThat(parsed.getTestFilePaths()).containsExactly("Test1.json", "Test2.json", "Test3.json");
        Assertions.assertThat(parsed.getTestCases()).hasSize(3);
        Assertions.assertThat(parsed.getTestCases().getFirst().getEndLine()).isGreaterThan(1);

        Assertions.assertThat(parsed.getProblemDetails().getRest().getEvaluatedHttpCalls()).isEqualTo(10);
        Assertions.assertThat(parsed.getProblemDetails().getRest().getOutputHttpCalls()).isEqualTo(3);
        Assertions.assertThat(parsed.getProblemDetails().getRest().getEndpointIds())
                .contains("POST:/pets", "GET:/pets/{id}");

        // Test 3 had status 953 (I/O error), which must map to empty httpStatus set in WFC CoveredEndpoint (0..599 invariant)
        Assertions.assertThat(parsed.getProblemDetails().getRest().getCoveredHttpStatus())
                .filteredOn(ce -> "Test3".equals(ce.getTestCaseId()))
                .singleElement()
                .satisfies(ce -> Assertions.assertThat(ce.getHttpStatus()).isEmpty());

        Assertions.assertThat(parsed.getFaults().getTotalNumber()).isEqualTo(2);
        Assertions.assertThat(parsed.getFaults().getFoundFaults())
                .extracting(ff -> ff.getFaultCategories().iterator().next().getCode())
                .containsExactlyInAnyOrder(
                        DefinedFaultCategory.HTTP_STATUS_500.getCode(),
                        DefinedFaultCategory.SECURITY_CALL_TIMEOUT.getCode());
        Assertions.assertThat(parsed.getWarnings()).hasSize(1);
    }
}
