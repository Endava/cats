package com.endava.cats.command;

import com.endava.cats.args.QualityGateArguments;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.report.BaselineComparisonEngine;
import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@QuarkusTest
class CompareCommandTest {

    @Test
    void shouldCompareApplesToApplesAndIgnoreUnsharedScopes(@TempDir Path tempDir) throws IOException {
        CatsTestCaseSummary baseOk = summary("Test 1", "GET", "/pets", "HappyPath", "success", null, 200);
        CatsTestCaseSummary baseKnownError = summary("Test 2", "POST", "/pets", "NullValuesInFields", "error", "Unexpected response code", 500);
        CatsTestCaseSummary baseOnlyScope = summary("Test 3", "DELETE", "/pets/{id}", "HappyPath", "error", "Server error", 500);

        CatsTestCaseSummary targetRegressed = summary("Test 10", "GET", "/pets", "HappyPath", "error", "Unexpected response code", 500);
        CatsTestCaseSummary targetSameKnownError = summary("Test 11", "POST", "/pets", "NullValuesInFields", "error", "Unexpected response code", 500);
        CatsTestCaseSummary targetNewScopeError = summary("Test 12", "PUT", "/pets/{id}", "HappyPath", "error", "Server error", 500);

        Path baseDir = tempDir.resolve("baseline");
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(baseDir);
        Files.createDirectories(targetDir);

        writeReport(baseDir.resolve(BaselineComparisonEngine.SUMMARY_FILE_NAME),
                List.of(baseOk, baseKnownError, baseOnlyScope), 10);
        writeReport(targetDir.resolve(BaselineComparisonEngine.SUMMARY_FILE_NAME),
                List.of(targetRegressed, targetSameKnownError, targetNewScopeError), 12);

        CompareCommand cmd = new CompareCommand();
        cmd.baseline = baseDir.toFile();
        cmd.target = targetDir.toFile();
        cmd.run();

        Assertions.assertThat(cmd.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
        BaselineComparisonEngine.ComparisonResult result = cmd.getLastResult();
        Assertions.assertThat(result.sharedScopes()).isEqualTo(2);
        Assertions.assertThat(result.onlyInBaselineScopes()).isEqualTo(1);
        Assertions.assertThat(result.onlyInTargetScopes()).isEqualTo(1);
        Assertions.assertThat(result.newErrorCount()).isEqualTo(1);
        Assertions.assertThat(result.unchangedErrors()).isEqualTo(1);
        Assertions.assertThat(result.executionTimeDeltaSec()).isEqualTo(2);
    }

    @Test
    void shouldPassWhenLegacyErrorsAreUnchangedOrResolvedAndSupportJsonOutput(@TempDir Path tempDir) throws IOException {
        CatsTestCaseSummary baseKnownError = summary("Test 1", "POST", "/pets", "NullValuesInFields", "error", "Server crash", 500);
        CatsTestCaseSummary targetFixed = summary("Test 1", "POST", "/pets", "NullValuesInFields", "success", null, 400);

        Path baseFile = tempDir.resolve("base-summary.json");
        Path targetFile = tempDir.resolve("target-summary.json");
        writeReport(baseFile, List.of(baseKnownError), 8);
        writeReport(targetFile, List.of(targetFixed), 7);

        CompareCommand cmd = new CompareCommand();
        cmd.baseline = baseFile.toFile();
        cmd.target = targetFile.toFile();
        cmd.json = true;
        cmd.run();

        Assertions.assertThat(cmd.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
        Assertions.assertThat(cmd.getLastResult().newErrorCount()).isZero();
        Assertions.assertThat(cmd.getLastResult().resolvedErrorCount()).isEqualTo(1);

        QualityGateArguments qg = new QualityGateArguments();
        qg.setBaseline(baseFile.toFile());
        qg.setBaselineRegressionDetected(false);
        Assertions.assertThat(qg.shouldFailBuild(5, 2)).isFalse();
        Assertions.assertThat(qg.getQualityGateDescription()).contains("Baseline comparison:");
    }

    private static void writeReport(Path file, List<CatsTestCaseSummary> cases, long execTime) throws IOException {
        CatsTestReport report = CatsTestReport.builder()
                .testCases(cases)
                .executionTime(execTime)
                .build();
        Files.writeString(file, JsonUtils.GSON.toJson(report));
    }

    private static CatsTestCaseSummary summary(String id, String method, String path, String fuzzer,
                                               String result, String reason, int code) {
        CatsTestCase tc = new CatsTestCase();
        tc.setTestId(id);
        tc.setContractPath(path);
        tc.setFuzzer(fuzzer);
        tc.setResult(result);
        tc.setResultReason(reason);
        tc.setRequest(CatsRequest.builder().httpMethod(method).build());
        tc.setResponse(CatsResponse.builder().responseCode(code).responseTimeInMs(10).body("{}").build());
        return CatsTestCaseSummary.fromCatsTestCase(tc);
    }
}
