package com.endava.cats.command;

import com.endava.cats.fuzzer.special.FunctionalFlowParser;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@QuarkusTest
class TriageCommandTest {

    @Test
    void shouldClusterRootCausesMineRefDataGenerateContractPatchAndSynthesizeCorrelations(@TempDir Path tempDir) throws Exception {
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
                  "resultReason": "Response code is undocumented",
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
        Path exportedRefData = tempDir.resolve("refData.yml");
        Path exportedPatch = tempDir.resolve("contract-patch.yml");
        Path exportedSh = tempDir.resolve("reproducers.sh");

        TriageCommand triageCommand = new TriageCommand();
        triageCommand.reportFolder = tempDir.toString();
        triageCommand.exportCorrelationsFile = exportedCorr.toString();
        triageCommand.exportRefDataFile = exportedRefData.toString();
        triageCommand.exportContractPatchFile = exportedPatch.toString();
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
        Assertions.assertThat(FunctionalFlowParser.parseCorrelationFile(exportedCorr.toFile()))
                .hasSize(1)
                .first()
                .satisfies(rule -> {
                    Assertions.assertThat(rule.paths()).containsExactly("/billing-plans");
                    Assertions.assertThat(rule.when()).isEqualTo("planType == 'RECURRING'");
                    Assertions.assertThat(rule.set()).containsKey("billingSchedule");
                });
        Assertions.assertThat(Files.readString(exportedRefData))
                .contains("\"/customer-groups/{groupId}\":", "groupId: \"grp_1\"");
        Assertions.assertThat(Files.readString(exportedPatch))
                .contains("/customer-groups/{groupId}:", "delete:", "'204':");
        Assertions.assertThat(Files.readString(exportedSh))
                .contains("#!/usr/bin/env bash", "curl -i -X POST", "'http://localhost:8080/billing-plans'");
    }

    @Test
    void shouldVerifyFixesAgainstLiveServer(@TempDir Path tempDir) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/billing-plans", exchange -> {
            exchange.sendResponseHeaders(400, 2);
            exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            Files.writeString(tempDir.resolve("Test1.json"), """
                    {
                      "testId": "Test 1",
                      "result": "error",
                      "resultReason": "Unexpected response code: 500",
                      "fuzzer": "DummyContentTypeHeaders",
                      "contractPath": "/billing-plans",
                      "path": "/billing-plans",
                      "request": {
                        "httpMethod": "POST",
                        "url": "http://localhost:8080/billing-plans",
                        "headers": [{"key": "Content-Type", "value": "application/json"}],
                        "payload": "{}",
                        "sendBody": true
                      },
                      "response": {
                        "responseCode": 500,
                        "jsonBody": {"message": "Internal error"}
                      }
                    }
                    """, StandardCharsets.UTF_8);

            TriageCommand triageCommand = new TriageCommand();
            triageCommand.reportFolder = tempDir.toString();
            triageCommand.verify = true;
            triageCommand.serverUrl = "http://127.0.0.1:" + port;
            triageCommand.json = true;

            triageCommand.run();

            Assertions.assertThat(triageCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
            Assertions.assertThat(triageCommand.getLastReport().clusters())
                    .singleElement()
                    .satisfies(cluster -> {
                        Assertions.assertThat(cluster.verificationStatus()).isEqualTo("FIXED");
                        Assertions.assertThat(cluster.verifiedStatusCode()).isEqualTo(400);
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldSupportJsonOutputAndHandleMissingDirectory(@TempDir Path tempDir) {
        TriageCommand missingCmd = new TriageCommand();
        missingCmd.reportFolder = tempDir.resolve("does-not-exist").toString();
        missingCmd.run();
        Assertions.assertThat(missingCmd.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE);

        TriageCommand emptyCmd = new TriageCommand();
        emptyCmd.reportFolder = tempDir.toString();
        emptyCmd.json = true;
        emptyCmd.includeWarnings = true;
        emptyCmd.run();
        Assertions.assertThat(emptyCmd.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
        Assertions.assertThat(emptyCmd.getLastReport().totalClusters()).isZero();
    }

    @Test
    void shouldClassifyAuthGapsHeaderCollisionsUnresolved404OmitCorrelationsAndContractDrift(@TempDir Path tempDir) throws Exception {
        // 2XX POST with numeric id to exercise primitiveToJavaObject(number)
        Files.writeString(tempDir.resolve("Test1.json"), """
                {
                  "testId": "Test 1",
                  "result": "success",
                  "fuzzer": "HappyPath",
                  "contractPath": "/orders",
                  "path": "/orders",
                  "request": {"httpMethod": "POST", "url": "http://localhost:8080/orders", "sendBody": true},
                  "response": {"responseCode": 201, "jsonBody": {"id": 9001, "activeId": true}}
                }
                """, StandardCharsets.UTF_8);

        // 401 Auth failure
        Files.writeString(tempDir.resolve("Test2.json"), """
                {
                  "testId": "Test 2",
                  "result": "error",
                  "fuzzer": "HappyPath",
                  "contractPath": "/orders",
                  "path": "/orders",
                  "request": {"httpMethod": "GET", "url": "http://localhost:8080/orders", "sendBody": false},
                  "response": {"responseCode": 401, "jsonBody": {"message": "Unauthorized"}}
                }
                """, StandardCharsets.UTF_8);

        // Header correlation collision
        Files.writeString(tempDir.resolve("Test3.json"), """
                {
                  "testId": "Test 3",
                  "result": "error",
                  "fuzzer": "HappyPath",
                  "contractPath": "/orders",
                  "path": "/orders",
                  "request": {"httpMethod": "POST", "url": "http://localhost:8080/orders", "sendBody": true},
                  "response": {"responseCode": 400, "jsonBody": {"message": "Invalid correlation header"}},
                  "requestProvenance": {
                    "runtimeCorrelations": [{
                      "sourceMethod": "POST",
                      "sourcePath": "/orders",
                      "sourceLocation": "response.body.$.id",
                      "target": {"location": "HEADER", "name": "X-Request-Id"},
                      "value": "9001"
                    }]
                  }
                }
                """, StandardCharsets.UTF_8);

        // Unresolved 404 on /orders/{orderId} (where POST /orders succeeded)
        Files.writeString(tempDir.resolve("Test4.json"), """
                {
                  "testId": "Test 4",
                  "result": "error",
                  "fuzzer": "HappyPath",
                  "contractPath": "/orders/{orderId}",
                  "path": "/orders/{orderId}",
                  "request": {"httpMethod": "GET", "url": "http://localhost:8080/orders/9999", "sendBody": false},
                  "response": {"responseCode": 404, "jsonBody": {"message": "Order not found"}}
                }
                """, StandardCharsets.UTF_8);

        // 422 with 'must be null' -> synthesizes REMOVE (omit) rule
        Files.writeString(tempDir.resolve("Test5.json"), """
                {
                  "testId": "Test 5",
                  "result": "error",
                  "expectedResult": "Should return 2XX",
                  "fuzzer": "OnlyRequiredFields",
                  "contractPath": "/orders",
                  "path": "/orders",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://localhost:8080/orders",
                    "payload": "{\\"orderType\\":\\"DIGITAL\\",\\"shippingAddress\\":\\"123 Main St\\"}",
                    "sendBody": true
                  },
                  "response": {"responseCode": 422, "jsonBody": {"message": "'shippingAddress' must be null for DIGITAL orders"}}
                }
                """, StandardCharsets.UTF_8);

        // Contract mismatch (non-5XX, non-404, non-401, non-seed 400)
        Files.writeString(tempDir.resolve("Test6.json"), """
                {
                  "testId": "Test 6",
                  "result": "error",
                  "resultReason": "Unexpected response code: 200",
                  "expectedResult": "Should return 4XX",
                  "fuzzer": "InvalidValuesInEnumsFields",
                  "contractPath": "/pet",
                  "path": "/pet",
                  "request": {"httpMethod": "PUT", "url": "http://localhost:8080/pet", "sendBody": true},
                  "response": {"responseCode": 200, "jsonBody": {"status": "accepted"}}
                }
                """, StandardCharsets.UTF_8);

        TriageCommand cmd = new TriageCommand();
        cmd.reportFolder = tempDir.toString();
        cmd.contractPath = "src/test/resources/petstore.yml";
        cmd.run();

        Assertions.assertThat(cmd.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
        Assertions.assertThat(cmd.getLastReport().clusters()).extracting(TriageCommand.RootCauseCluster::category)
                .contains("HEADER_CORRELATION_COLLISION", "CROSS_FIELD_CONSTRAINT_4XX",
                        "AUTH_CONFIGURATION_GAP", "UNRESOLVED_PATH_PARAM_404", "CONTRACT_MISMATCH");
        Assertions.assertThat(cmd.getLastReport().synthesizedCorrelationsYaml()).contains("omit:", "- \"shippingAddress\"");
        Assertions.assertThat(cmd.getLastReport().minedRefDataYaml()).contains("id: 9001", "activeId: true");
    }
}
