package com.endava.cats.command;

import com.endava.cats.command.triage.RootCauseCluster;
import com.endava.cats.command.triage.SynthesizedCorrelationRule;
import com.endava.cats.command.triage.TriageReport;
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
        TriageReport report = triageCommand.getLastReport();
        Assertions.assertThat(report).isNotNull();
        Assertions.assertThat(report.totalTestsScanned()).isEqualTo(5);
        Assertions.assertThat(report.totalErrors()).isEqualTo(4);
        Assertions.assertThat(report.clusters()).extracting(RootCauseCluster::category)
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
                .contains("No 2XX path parameters or resource IDs mined")
                .doesNotContain("grp_1");
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
                    "payload": "{\\"delivery\\":{\\"mode\\":\\"DIGITAL\\"},\\"shippingAddress\\":\\"123 Main St\\"}",
                    "sendBody": true
                  },
                  "response": {"responseCode": 422, "jsonBody": {"message": "Shipping address must be null for DIGITAL orders"}}
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

        Files.writeString(tempDir.resolve("Test7.json"), """
                {
                  "testId": "Test 7",
                  "result": "error",
                  "resultReason": "Unexpected response code: 200",
                  "expectedResult": "Should return 4XX",
                  "fuzzer": "NonEnumeratedFieldsFuzzer",
                  "contractPath": "/pet",
                  "path": "/pet",
                  "request": {"httpMethod": "PUT", "url": "http://localhost:8080/pet", "sendBody": true},
                  "response": {"responseCode": 200, "jsonBody": {"status": "accepted"}}
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(tempDir.resolve("Test8.json"), """
                {
                  "testId": "Test 8",
                  "result": "error",
                  "resultReason": "Not found",
                  "expectedResult": "Should return 2XX",
                  "fuzzer": "HappyPath",
                  "contractPath": "/orders/bulk",
                  "path": "/orders/bulk",
                  "request": {"httpMethod": "POST", "url": "http://localhost:8080/orders/bulk", "payload": "[\\"ord-1\\"]", "sendBody": true},
                  "response": {"responseCode": 404, "jsonBody": {"message": "Order not found"}}
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(tempDir.resolve("Test9.json"), """
                {
                  "testId": "Test 9",
                  "result": "error",
                  "resultReason": "Not found",
                  "expectedResult": "Should return 2XX",
                  "fuzzer": "HappyPath",
                  "contractPath": "/users/{id}/tags",
                  "path": "/users/{id}/tags",
                  "request": {"httpMethod": "POST", "url": "http://localhost:8080/users/999/tags", "payload": "[\\"tag-1\\"]", "sendBody": true},
                  "response": {"responseCode": 404, "jsonBody": {"message": "User not found"}}
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(tempDir.resolve("Test10.json"), """
                {
                  "testId": "Test 10",
                  "result": "error",
                  "resultReason": "Unexpected response code: 400",
                  "expectedResult": "Should return 4XX",
                  "fuzzer": "StringFieldsRightBoundary",
                  "contractPath": "/orders",
                  "path": "/orders",
                  "request": {"httpMethod": "POST", "url": "http://localhost:8080/orders", "payload": "{\\"delivery\\":{\\"mode\\":\\"DIGITAL\\"}}", "sendBody": true},
                  "response": {"responseCode": 400, "jsonBody": {"message": "Invalid field length"}}
                }
                """, StandardCharsets.UTF_8);

        TriageCommand cmd = new TriageCommand();
        cmd.reportFolder = tempDir.toString();
        cmd.contractPath = "src/test/resources/petstore.yml";
        cmd.run();

        Assertions.assertThat(cmd.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
        Assertions.assertThat(cmd.getLastReport().clusters()).extracting(RootCauseCluster::category)
                .contains("HEADER_CORRELATION_COLLISION", "CROSS_FIELD_CONSTRAINT_4XX",
                        "AUTH_CONFIGURATION_GAP", "UNRESOLVED_PATH_PARAM_404",
                        "UNRESOLVED_BODY_REFERENCE_404", "VALIDATION_BYPASS_2XX", "CONTRACT_MISMATCH");
        RootCauseCluster enumCluster = cmd.getLastReport().clusters().stream()
                .filter(c -> "VALIDATION_BYPASS_2XX".equals(c.category()) && c.fuzzers().contains("InvalidValuesInEnumsFields"))
                .findFirst().orElseThrow();
        Assertions.assertThat(enumCluster.remediation()).contains("--allowInvalidEnumValues");
        RootCauseCluster nonEnumCluster = cmd.getLastReport().clusters().stream()
                .filter(c -> "VALIDATION_BYPASS_2XX".equals(c.category()) && c.fuzzers().contains("NonEnumeratedFieldsFuzzer"))
                .findFirst().orElseThrow();
        Assertions.assertThat(nonEnumCluster.remediation()).doesNotContain("--allowInvalidEnumValues");
        Assertions.assertThat(cmd.getLastReport().clusters())
                .anyMatch(c -> "UNRESOLVED_BODY_REFERENCE_404".equals(c.category()) && "POST /orders/bulk".equals(c.operation()))
                .anyMatch(c -> "UNRESOLVED_PATH_PARAM_404".equals(c.category()) && "POST /users/{id}/tags".equals(c.operation()))
                .anyMatch(c -> "CONTRACT_MISMATCH".equals(c.category()) && c.fuzzers().contains("StringFieldsRightBoundary"));
        Assertions.assertThat(cmd.getLastReport().synthesizedCorrelationsYaml())
                .contains("when: \"delivery.mode == 'DIGITAL'\"", "omit:", "- \"shippingAddress\"");
        Assertions.assertThat(cmd.getLastReport().minedRefDataYaml())
                .contains("\"/orders/{orderId}\":", "orderId: 9001")
                .doesNotContain("activeId", "\"/orders/{id}\"", "9999");
    }

    @Test
    void shouldHandleVerifyFailuresUnquotedFieldsEnumDiscriminatorsAndStatusDescriptions(@TempDir Path tempDir) throws Exception {
        // Test 1 & Test 2: same 500 crash to hit accumulator merge + unfixed crash exit code
        for (int i = 1; i <= 2; i++) {
            Files.writeString(tempDir.resolve("Test" + i + ".json"), """
                    {
                      "testId": "Test %d",
                      "result": "error",
                      "resultReason": "Unexpected response code: 500",
                      "fuzzer": "Fuzzer%d",
                      "contractPath": "/crashes",
                      "path": "/crashes",
                      "fullRequestPath": "http://127.0.0.1:1/crashes",
                      "request": {"httpMethod": "GET", "sendBody": false},
                      "response": {"responseCode": 500, "body": "Internal crash"}
                    }
                    """.formatted(i, i), StandardCharsets.UTF_8);
        }

        // Test 3: 422 with unquoted multi-word required field not in payload + enum-like constant discriminator
        Files.writeString(tempDir.resolve("Test3.json"), """
                {
                  "testId": "Test 3",
                  "result": "error",
                  "resultReason": "Unexpected response code: 422",
                  "expectedResult": "Should return 2XX",
                  "fuzzer": "HappyPath",
                  "contractPath": "/invoices",
                  "path": "/invoices",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://127.0.0.1:1/invoices",
                    "payload": "[{\\"planMarker\\":\\"ENTERPRISE_TIER\\"}]",
                    "sendBody": true
                  },
                  "response": {"responseCode": 422, "jsonBody": {"message": "tax number is required"}}
                }
                """, StandardCharsets.UTF_8);

        // Test 4: 400 on /invoices with expected 2XX to hit pathsWithSynthesizedCorrelations branch
        Files.writeString(tempDir.resolve("Test4.json"), """
                {
                  "testId": "Test 4",
                  "result": "error",
                  "resultReason": "Unexpected response code: 400",
                  "expectedResult": "Should return 2XX",
                  "fuzzer": "CustomFuzzer",
                  "contractPath": "/invoices",
                  "path": "/invoices",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://127.0.0.1:1/invoices",
                    "payload": "{\\"planMarker\\":\\"ENTERPRISE_TIER\\",\\"nested\\":{\\"subType\\":\\"B2B\\"}}",
                    "sendBody": true
                  },
                  "response": {"responseCode": 400, "jsonBody": {"message": "code is required"}}
                }
                """, StandardCharsets.UTF_8);

        // Test 5..14: undocumented status codes to cover defaultStatusDescription switch branches
        int[] codes = {200, 201, 202, 204, 400, 401, 403, 404, 409, 415, 422, 429};
        for (int idx = 0; idx < codes.length; idx++) {
            int code = codes[idx];
            Files.writeString(tempDir.resolve("Test" + (10 + idx) + ".json"), """
                    {
                      "testId": "Test %d",
                      "result": "warn",
                      "resultReason": "Undocumented response code",
                      "fuzzer": "HappyPath",
                      "contractPath": "/drift",
                      "path": "/drift",
                      "request": {"httpMethod": "GET", "url": "http://127.0.0.1:1/drift", "sendBody": false},
                      "response": {"responseCode": %d}
                    }
                    """.formatted(10 + idx, code), StandardCharsets.UTF_8);
        }

        TriageCommand cmd = new TriageCommand();
        cmd.reportFolder = tempDir.toString();
        cmd.contractPath = "does-not-exist-contract.yml";
        cmd.verify = true;
        cmd.serverUrl = "http://127.0.0.1:1/base";
        cmd.customHeaders.put("X-Override", "1");
        cmd.run();

        Assertions.assertThat(cmd.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
        Assertions.assertThat(cmd.getLastReport().synthesizedCorrelationsYaml())
                .contains("taxNumber", "code");
        Assertions.assertThat(cmd.getLastReport().contractPatchYaml())
                .contains("OK", "Created", "Accepted", "No Content", "Bad Request",
                        "Unauthorized", "Forbidden", "Not Found", "Conflict",
                        "Unsupported Media Type", "Unprocessable Entity", "HTTP 429");

        // Cover OpenAPI contract drift detection when openAPI is supplied (472-481),
        // minedRefData remediation on 404 (715-717), nested field match (887-890),
        // nested discriminator candidate (938-950), and String id mining (448)
        Path openApiDriftDir = tempDir.resolve("openapi-drift");
        Files.createDirectories(openApiDriftDir);
        Files.writeString(openApiDriftDir.resolve("Test1.json"), """
                {
                  "testId": "Test 1",
                  "result": "success",
                  "fuzzer": "HappyPath",
                  "contractPath": "/pets",
                  "path": "/pets",
                  "request": {"httpMethod": "POST", "url": "http://localhost:8080/pets", "sendBody": true},
                  "response": {"responseCode": 200, "jsonBody": {"id": "pet-str-1"}}
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(openApiDriftDir.resolve("Test2.json"), """
                {
                  "testId": "Test 2",
                  "result": "error",
                  "fuzzer": "HappyPath",
                  "contractPath": "/pets/{id}",
                  "path": "/pets/{id}",
                  "request": {"httpMethod": "GET", "url": "invalid url with spaces", "sendBody": false},
                  "response": {"responseCode": 404, "jsonBody": {"message": "Not found"}}
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(openApiDriftDir.resolve("Test3.json"), """
                {
                  "testId": "Test 3",
                  "result": "error",
                  "expectedResult": "Should return 2XX",
                  "fuzzer": "HappyPath",
                  "contractPath": "/pets",
                  "path": "/pets",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://localhost:8080/pets",
                    "payload": "{\\"wrapper\\":{\\"targetField\\":\\"\\",\\"channelType\\":\\"WEB\\"}}",
                    "sendBody": true
                  },
                  "response": {"responseCode": 422, "jsonBody": {"message": "target field must not be blank"}}
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(openApiDriftDir.resolve("Test4.json"), """
                {
                  "testId": "Test 4",
                  "result": "error",
                  "expectedResult": "Should return 4XX without 2XX",
                  "fuzzer": "RemoveFields",
                  "contractPath": "/pets",
                  "path": "/pets",
                  "request": {"httpMethod": "POST", "url": "http://localhost:8080/pets", "sendBody": true},
                  "response": {"responseCode": 202, "jsonBody": {}}
                }
                """, StandardCharsets.UTF_8);

        Path driftSpec = openApiDriftDir.resolve("openapi.yml");
        Files.writeString(driftSpec, """
                openapi: 3.0.0
                info:
                  title: Drift Spec
                  version: 1.0.0
                paths:
                  /pets:
                    post:
                      responses:
                        '200':
                          description: OK
                """, StandardCharsets.UTF_8);

        TriageCommand driftCmd = new TriageCommand();
        driftCmd.reportFolder = openApiDriftDir.toString();
        driftCmd.contractPath = driftSpec.toString();
        driftCmd.run();
        Assertions.assertThat(driftCmd.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
        Assertions.assertThat(driftCmd.getLastReport().synthesizedCorrelationsYaml())
                .contains("wrapper.channelType == 'WEB'", "wrapper.targetField");
        Assertions.assertThat(driftCmd.getLastReport().contractPatchYaml()).contains("'202':");

        // Cover 404 remediation when producer exists in spec and no mined refData (line 719),
        // similar diagnosis merging into existing cluster (line 371), and omit correlation action (line 817)
        Path extraTriageDir = tempDir.resolve("extra-triage");
        Files.createDirectories(extraTriageDir);
        Files.writeString(extraTriageDir.resolve("Test1.json"), """
                {
                  "testId": "Test 1",
                  "result": "error",
                  "fuzzer": "HappyPath",
                  "contractPath": "/pets/{id}",
                  "path": "/pets/10",
                  "request": {"httpMethod": "GET", "url": "http://localhost:8080/pets/10"},
                  "response": {"responseCode": 404, "jsonBody": {"message": "Pet 10 not found at /pets/10"}}
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(extraTriageDir.resolve("Test2.json"), """
                {
                  "testId": "Test 2",
                  "result": "error",
                  "fuzzer": "HappyPath",
                  "contractPath": "/pets/{id}",
                  "path": "/pets/20",
                  "request": {"httpMethod": "GET", "url": "http://localhost:8080/pets/20"},
                  "response": {"responseCode": 404, "jsonBody": {"message": "Pet 20 not found at /pets/20"}}
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(extraTriageDir.resolve("Test3.json"), """
                {
                  "testId": "Test 3",
                  "result": "error",
                  "fuzzer": "HappyPath",
                  "contractPath": "/pets",
                  "path": "/pets",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://localhost:8080/pets",
                    "payload": "{\\"extraField\\":\\"1\\",\\"kind\\":\\"STATIC_KIND\\"}"
                  },
                  "response": {"responseCode": 422, "jsonBody": {"message": "'extraField' must be null when kind is STATIC_KIND"}}
                }
                """, StandardCharsets.UTF_8);

        TriageCommand extraCmd = new TriageCommand();
        extraCmd.reportFolder = extraTriageDir.toString();
        extraCmd.contractPath = driftSpec.toString();
        extraCmd.run();
        Assertions.assertThat(extraCmd.getLastReport().synthesizedCorrelationsYaml()).contains("omit:");
    }

    @Test
    void shouldSynthesizeCorrelationsWithErrorCodePrefixesMutationTargetsAndMentionedDiscriminators(@TempDir Path tempDir) throws Exception {
        // 1. Error-code prefix ("XEL-4220: Operator is required for CONDITION") + removed field in mutationTargets
        Files.writeString(tempDir.resolve("Test1.json"), """
                {
                  "testId": "Test 1",
                  "result": "error",
                  "resultReason": "Unexpected response code: 422",
                  "expectedResult": "Should return 2XX as required fields were not removed",
                  "fuzzer": "RemoveFields",
                  "contractPath": "/risk-rules",
                  "path": "/risk-rules",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://localhost:8098/risk-rules",
                    "payload": "{\\"ruleType\\":\\"STATIC\\",\\"expression\\":{\\"type\\":\\"CONDITION\\",\\"field\\":\\"country\\"}}",
                    "sendBody": true
                  },
                  "response": {
                    "responseCode": 422,
                    "jsonBody": {"httpCode": 422, "errorCode": "XEL-4220", "message": "Operator is required for CONDITION"}
                  },
                  "requestProvenance": {
                    "mutationTargets": [{"location": "BODY", "name": "expression#operator"}],
                    "runtimeCorrelations": []
                  }
                }
                """, StandardCharsets.UTF_8);

        // 2. Duplicate field value error ("XEL-4220: Watchlist with name 'B' and type COUNTRY already exists")
        Files.writeString(tempDir.resolve("Test2.json"), """
                {
                  "testId": "Test 2",
                  "result": "error",
                  "resultReason": "Unexpected response code: 422",
                  "expectedResult": "Should return [2XX]",
                  "fuzzer": "MinLengthExactValuesInStringFields",
                  "contractPath": "/risk-watchlists",
                  "path": "/risk-watchlists",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://localhost:8098/risk-watchlists",
                    "payload": "{\\"name\\":\\"B\\",\\"type\\":\\"COUNTRY\\"}",
                    "sendBody": true
                  },
                  "response": {
                    "responseCode": 422,
                    "jsonBody": {"httpCode": 422, "errorCode": "XEL-4220", "message": "Watchlist with name 'B' and type COUNTRY already exists"}
                  }
                }
                """, StandardCharsets.UTF_8);

        // 3. Operator value compatibility ("XEL-4220: Operator GT requires a numeric value") + case-variant enum
        Files.writeString(tempDir.resolve("Test3.json"), """
                {
                  "testId": "Test 3",
                  "result": "error",
                  "resultReason": "Unexpected response code: 422",
                  "expectedResult": "Should return [2XX]",
                  "fuzzer": "EnumCaseVariantFields",
                  "contractPath": "/risk-rules",
                  "path": "/risk-rules",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://localhost:8098/risk-rules",
                    "payload": "{\\"ruleType\\":\\"STATIC\\",\\"expression\\":{\\"type\\":\\"CONDITION\\",\\"field\\":\\"country\\",\\"operator\\":\\"gt\\",\\"value\\":[\\"KP\\"]}}",
                    "sendBody": true
                  },
                  "response": {
                    "responseCode": 422,
                    "jsonBody": {"httpCode": 422, "errorCode": "XEL-4220", "message": "Operator GT requires a numeric value"}
                  }
                }
                """, StandardCharsets.UTF_8);

        // 4. Composite NOT child requirement ("XEL-4220: NOT expression must have exactly one child, found 2")
        Files.writeString(tempDir.resolve("Test4.json"), """
                {
                  "testId": "Test 4",
                  "result": "error",
                  "resultReason": "Unexpected response code: 422",
                  "expectedResult": "Should return [2XX]",
                  "fuzzer": "EnumCaseVariantFields",
                  "contractPath": "/risk-rules",
                  "path": "/risk-rules",
                  "request": {
                    "httpMethod": "POST",
                    "url": "http://localhost:8098/risk-rules",
                    "payload": "{\\"ruleType\\":\\"STATIC\\",\\"expression\\":{\\"type\\":\\"noT\\",\\"children\\":[{},{}]}}",
                    "sendBody": true
                  },
                  "response": {
                    "responseCode": 422,
                    "jsonBody": {"httpCode": 422, "errorCode": "XEL-4220", "message": "NOT expression must have exactly one child, found 2"}
                  }
                }
                """, StandardCharsets.UTF_8);

        // 5. Negative fuzzer expecting 4XX should NOT synthesize correlation rules when --include-warnings is enabled
        Files.writeString(tempDir.resolve("Test5.json"), """
                {
                  "testId": "Test 5",
                  "result": "warn",
                  "resultReason": "Response content type not matching the contract",
                  "expectedResult": "Should return [4XX]",
                  "fuzzer": "InvalidValuesInEnumsFields",
                  "contractPath": "/risk-watchlists",
                  "path": "/risk-watchlists",
                  "request": {
                    "httpMethod": "GET",
                    "url": "http://localhost:8098/risk-watchlists?sort_direction=HYI",
                    "payload": "{\\"sort_direction\\":\\"HYI\\"}",
                    "sendBody": false
                  },
                  "response": {
                    "responseCode": 422,
                    "jsonBody": {"httpCode": 422, "errorCode": "XEL-4220", "message": "Invalid sort direction 'HYI'. Valid values: [DESC, ASC]"}
                  }
                }
                """, StandardCharsets.UTF_8);

        TriageCommand cmd = new TriageCommand();
        cmd.reportFolder = tempDir.toString();
        cmd.includeWarnings = true;
        cmd.json = true;
        cmd.run();

        Assertions.assertThat(cmd.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
        TriageReport report = cmd.getLastReport();
        Assertions.assertThat(report.synthesizedCorrelations())
                .extracting(
                        SynthesizedCorrelationRule::path,
                        SynthesizedCorrelationRule::whenField,
                        SynthesizedCorrelationRule::whenValue,
                        SynthesizedCorrelationRule::targetField)
                .containsExactly(
                        Assertions.tuple("/risk-rules", "expression.type", "CONDITION", "expression.operator"),
                        Assertions.tuple("/risk-watchlists", "name", null, "name"),
                        Assertions.tuple("/risk-rules", "expression.operator", "GT", "expression.value"),
                        Assertions.tuple("/risk-rules", "expression.type", "NOT", "expression.children"));
    }
}
