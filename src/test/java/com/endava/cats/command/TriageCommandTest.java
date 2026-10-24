package com.endava.cats.command;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@QuarkusTest
class TriageCommandTest {

    @Test
    void shouldClusterRootCausesSynthesizeCorrelationRulesAndExportReproducers(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("Test1.json"), """
                {
                  "testId": "Test 1",
                  "result": "error",
                  "resultReason": "Unexpected response code: 422",
                  "expectedResult": "Should return 2XX",
                  "fuzzer": "HappyPath",
                  "contractPath": "/billing-plans",
                  "path": "/billing-plans",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://localhost:8080/billing-plans",
                    "headers": [{"key": "Content-Type", "value": "application/json"}],
                    "payload": "{\\"planType\\":\\"RECURRING\\",\\"name\\":\\"Plan-1\\"}",
                    "sendBody": true
                  },
                  "response": {
                    "responseCode": 422,
                    "jsonBody": {"message": "'billingSchedule' must not be null"}
                  }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(tempDir.resolve("Test2.json"), """
                {
                  "testId": "Test 2",
                  "result": "warn",
                  "fuzzer": "RemoveHeaders",
                  "contractPath": "/customer-groups/{groupId}",
                  "path": "/customer-groups/{groupId}",
                  "request": {
                    "httpMethod": "DELETE",
                    "url": "http://localhost:8080/customer-groups/grp_1",
                    "sendBody": false
                  },
                  "response": {
                    "responseCode": 204
                  }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(tempDir.resolve("Test3.json"), """
                {
                  "testId": "Test 3",
                  "result": "error",
                  "resultReason": "Not found",
                  "fuzzer": "ExtraHeaders",
                  "contractPath": "/customer-groups/{groupId}/members",
                  "path": "/customer-groups/{groupId}/members",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://localhost:8080/customer-groups/grp_1/members",
                    "payload": "{\\"value\\":\\"US\\"}",
                    "sendBody": true
                  },
                  "response": {
                    "responseCode": 404,
                    "jsonBody": {"message": "Group not found"}
                  }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(tempDir.resolve("Test4.json"), """
                {
                  "testId": "Test 4",
                  "result": "error",
                  "resultReason": "Unexpected response code: 500",
                  "fuzzer": "DummyContentTypeHeaders",
                  "contractPath": "/billing-plans",
                  "path": "/billing-plans",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://localhost:8080/billing-plans",
                    "headers": [{"key": "Content-Type", "value": "multipart/form-data"}],
                    "payload": "{}",
                    "sendBody": true
                  },
                  "response": {
                    "responseCode": 500,
                    "jsonBody": {"message": "Failed to parse multipart servlet request"}
                  }
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(tempDir.resolve("Test5.json"), """
                {
                  "testId": "Test 5",
                  "result": "error",
                  "resultReason": "Not found",
                  "fuzzer": "HangulFillerFields",
                  "contractPath": "/billing-plans/{planId}",
                  "path": "/billing-plans/{planId}",
                  "request": {
                    "httpMethod": "DELETE",
                    "url": "http://localhost:8080/billing-plans/Plan-Alpha",
                    "sendBody": false
                  },
                  "response": {
                    "responseCode": 404,
                    "jsonBody": {"message": "Plan not found"}
                  },
                  "requestProvenance": {
                    "runtimeCorrelations": [{
                      "sourceMethod": "GET",
                      "sourcePath": "/billing-plans",
                      "sourceLocation": "response.body.$.items[].name",
                      "target": {"location": "PATH", "name": "planId"},
                      "value": "Plan-Alpha"
                    }]
                  }
                }
                """, StandardCharsets.UTF_8);

        Path exportedCorr = tempDir.resolve("correlations.yml");
        Path exportedSh = tempDir.resolve("reproducers.sh");

        TriageCommand triageCommand = new TriageCommand();
        triageCommand.reportFolder = tempDir.toString();
        triageCommand.exportCorrelationsFile = exportedCorr.toString();
        triageCommand.exportReproducersFile = exportedSh.toString();

        triageCommand.run();

        Assertions.assertThat(triageCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
        TriageCommand.TriageReport report = triageCommand.getLastReport();
        Assertions.assertThat(report).isNotNull();
        Assertions.assertThat(report.totalTestsScanned()).isEqualTo(5);
        Assertions.assertThat(report.totalErrors()).isEqualTo(4);
        Assertions.assertThat(report.clusters()).extracting(TriageCommand.RootCauseCluster::category)
                .containsExactly(
                        "SERVER_CRASH_5XX",
                        "CROSS_FIELD_CONSTRAINT_4XX",
                        "RESOURCE_POOL_DEPLETED_404",
                        "SUSPICIOUS_CORRELATION_404");
        Assertions.assertThat(report.synthesizedCorrelations()).hasSize(1);
        Assertions.assertThat(Files.readString(exportedCorr))
                .contains("correlations:", "- \"/billing-plans\"", "planType == 'RECURRING'", "billingSchedule:");
        Assertions.assertThat(com.endava.cats.fuzzer.special.FunctionalFlowParser.parseCorrelationFile(exportedCorr.toFile()))
                .hasSize(1)
                .first()
                .satisfies(rule -> {
                    Assertions.assertThat(rule.paths()).containsExactly("/billing-plans");
                    Assertions.assertThat(rule.when()).isEqualTo("planType == 'RECURRING'");
                    Assertions.assertThat(rule.set()).containsKey("billingSchedule");
                });
        Assertions.assertThat(Files.readString(exportedSh))
                .contains("#!/usr/bin/env bash", "curl -i -X POST", "'http://localhost:8080/billing-plans'");

        triageCommand.json = true;
        triageCommand.run();
        Assertions.assertThat(triageCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
    }

    @Test
    void shouldReturnSoftwareExitCodeWhenReportDirectoryDoesNotExist() {
        TriageCommand triageCommand = new TriageCommand();
        triageCommand.reportFolder = "src/test/resources/non-existent-report-folder";

        triageCommand.run();

        Assertions.assertThat(triageCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    }
}
