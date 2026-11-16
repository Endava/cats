package com.endava.cats.report;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.RequestTarget;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Synthetic runs shaped like the private reports of card 17 (a risk API with a discriminator bug, XSS on several
 * fields, undocumented 422s, a Tomcat HTML 400 page and 501 for CONNECT) and the V3 injection fixture.
 */
final class ProblemReportFixtures {
    private static final String TOMCAT_400 = "<!doctype html><html lang=\"en\"><head><title>HTTP Status 400 – Bad Request</title>"
            + "<style>body {font-family:Tahoma}</style></head><body><h1>HTTP Status 400 – Bad Request</h1></body></html>";
    private static final String VELOCITY = "Response does NOT match expected result. Response code [201] is documented, but response body "
            + "does NOT match the corresponding schema. Validation errors: $: no matching schema found for discriminator value 'VELOCITY'";

    static final OpenAPI RISK_CONTRACT = new OpenAPIV3Parser().readContents("""
            openapi: 3.0.0
            info: {title: risk, version: '1'}
            paths:
              /risk-rules:
                post:
                  responses:
                    '201': {description: created, content: {application/json: {schema: {$ref: '#/components/schemas/RuleResponse'}}}}
              /risk-rules/{ruleId}:
                put:
                  responses:
                    '200': {description: ok, content: {application/json: {schema: {$ref: '#/components/schemas/RuleResponse'}}}}
            components:
              schemas:
                RuleResponse: {type: object}
            """).getOpenAPI();

    private final List<CatsTestCase> tests = new ArrayList<>();
    private final AtomicInteger number = new AtomicInteger();

    static void writeSummary(TestCaseExporter exporter, List<CatsTestCaseSummary> summaries,
                             com.endava.cats.model.ExecutionSummary execution) {
        if (summaries == null) {
            exporter.writeSummary(null, execution, List.of());
            return;
        }
        ClusterCompute.assignErrorTemplates(summaries, exporter.catsGlobalContext.getOpenAPI());
        var problems = com.endava.cats.report.problem.ProblemCatalog.build(summaries,
                new com.endava.cats.report.problem.ResponseSchemaNames(exporter.catsGlobalContext.getOpenAPI()));
        exporter.writeSummary(summaries, execution, problems);
    }

    static List<CatsTestCase> riskApiRun() {
        ProblemReportFixtures run = new ProblemReportFixtures();
        run.add("success", "All Good!", 200, "HappyPath", "GET", "/risk-rules", "", "[]");
        for (int i = 0; i < 4; i++) {
            run.add("warn", "Not matching response schema", 201, i == 0 ? "HappyPath" : "RemoveFields", "POST", "/risk-rules", VELOCITY,
                    "{\"ruleType\":\"VELOCITY\",\"ruleId\":\"rule_%d\",\"description\":\"Flags transactions\"}".formatted(i));
            run.add("warn", "Not matching response schema", 200, "RemoveFields", "PUT", "/risk-rules/{ruleId}", VELOCITY.replace("[201]", "[200]"),
                    "{\"ruleType\":\"VELOCITY\",\"ruleId\":\"rule_%d\"}".formatted(i));
        }
        for (String field : List.of("description", "name")) {
            run.add("warn", "XSS payload reflected in response", 201, "XssInjectionInStringFields", "POST", "/risk-rules",
                    "Dangerous XSS payload was reflected in the response (response code: 201).", "{\"description\":\"<script>\"}", RequestTarget.body(field));
            run.add("warn", "XSS payload accepted without validation", 201, "XssInjectionInStringFields", "POST", "/risk-rules",
                    "API accepted XSS payload (response code 201).", "{\"description\":\"x\"}", RequestTarget.body(field));
        }
        run.add("warn", "XSS payload reflected in response", 200, "XssInjectionInStringFields", "GET", "/risk-watchlists",
                "Dangerous XSS payload was reflected in the response (response code: 200).", "{\"items\":[{\"watchlistId\":\"wl_01m6p5sr\"}]}", RequestTarget.query("name"));
        for (int i = 0; i < 3; i++) {
            run.add("warn", "Undocumented response code: 422", 422, "EmptyStringsInFields", "GET", "/risk-watchlists",
                    "Response code is from a list of expected codes, but it is undocumented: expected [400, 422], actual [422]",
                    "{\"code\":\"XEL-4221\",\"message\":\"'watchlistId' size must be between %d and 64\"}".formatted(i + 1), RequestTarget.query("watchlistId"));
        }
        run.add("warn", "Undocumented response code: 422", 422, "NullValuesInFields", "DELETE", "/risk-watchlists/{id}",
                "Response code is from a list of expected codes, but it is undocumented: expected [400, 422], actual [422]",
                "{\"code\":\"XEL-4222\",\"message\":\"'reason' must not be null\"}");
        for (int i = 0; i < 2; i++) {
            run.add("error", "Unexpected response code: 422", 422, "EnumCaseVariantFields", i == 0 ? "POST" : "PUT", i == 0 ? "/risk-rules" : "/risk-rules/{ruleId}",
                    "Response code is NOT from a list of expected codes for this FUZZER: expected [2XX], actual [422]",
                    "{\"code\":\"XEL-4220\",\"message\":\"Operator GT requires a numeric value\"}", RequestTarget.body("operator"));
        }
        run.add("error", "Unexpected response code: 422", 422, "HappyPath", "POST", "/risk-watchlists",
                "Response code is NOT from a list of expected codes for this FUZZER: expected [2XX], actual [422]",
                "{\"code\":\"XEL-4221\",\"message\":\"'watchlistId' size must be between 1 and 64\"}");
        run.add("error", "Heuristic IDOR signal detected", 200, "InsecureDirectObjectReferences", "POST", "/risk-evaluations",
                "Request with modified ID returned success [200] and the response contains the alternative ID [pay-1_other].",
                "{\"decision\":\"BYPASSED\"}", RequestTarget.body("paymentId"));
        for (String path : List.of("/risk-rules", "/risk-watchlists", "/risk-evaluations")) {
            run.add("warn", "Response content type not matching the contract", 400, "ControlCharsInStringFields", "POST", path,
                    "Response content type not matching the contract: expected [application/json], actual [text/html;charset=utf-8]", TOMCAT_400);
        }
        for (String path : List.of("/risk-rules", "/risk-watchlists")) {
            run.add("error", "Unexpected response code: 501", 501, "NonRestHttpMethods", "CONNECT", path,
                    "Unexpected response code for http method [CONNECT]: expected [405], actual [501]",
                    "<!doctype html><html><head><title>HTTP Status 501 – Not Implemented</title></head><body><h1>HTTP Status 501 – Not Implemented</h1></body></html>");
        }
        run.add("warn", "Unexpected response code: 200", 200, "DecimalFieldsRightBoundary", "POST", "/risk-evaluations",
                "Response code is NOT from a list of expected codes for this FUZZER: expected [400, 422], actual [200]", "{\"decision\":\"ALLOW\"}", RequestTarget.body("amount"));
        run.add("warn", "Unexpected response code: 201", 201, "DecimalFieldsRightBoundary", "POST", "/risk-rules",
                "Response code is NOT from a list of expected codes for this FUZZER: expected [400, 422], actual [201]", "{\"ruleId\":\"rule_9\"}", RequestTarget.body("score"));
        run.tests.get(3).setSameErrorAsBaselineTest("Test 2");
        run.tests.get(5).setSameErrorAsBaselineTest("Test 2");
        return run.tests;
    }

    /**
     * Card 17 section 13, V3: injection findings on 2xx where the fuzzer expected a 4xx, plus an error-based SQLi crash.
     */
    static List<CatsTestCase> injectionRun() {
        ProblemReportFixtures run = new ProblemReportFixtures();
        run.add("error", "SSTI injection vulnerability detected", 201, "SstiInjectionInStringFields", "POST", "/a",
                "Template expression {{7*7}} was evaluated to 49 in the response", "{\"name\":\"49\"}", RequestTarget.body("name"))
                .setConfidence(FindingConfidence.CONFIRMED);
        run.add("error", "SSTI injection vulnerability detected", 200, "SstiInjectionInStringFields", "PUT", "/a/{id}",
                "Template expression {{7*7}} was evaluated to 49, no baseline available", "{\"name\":\"49\"}", RequestTarget.body("name"))
                .setConfidence(FindingConfidence.FIRM);
        for (String field : List.of("name", "title")) {
            for (String path : List.of("/a", "/b")) {
                run.add("warn", "XSS payload reflected in response", 201, "XssInjectionInStringFields", "POST", path,
                        "Dangerous XSS payload was reflected in the response (response code: 201).", "{\"%s\":\"<script>alert(1)</script>\"}".formatted(field),
                        RequestTarget.body(field));
                run.add("warn", "XSS payload accepted without validation", 201, "XssInjectionInStringFields", "POST", path,
                        "API accepted XSS payload (response code 201).", "{\"id\":\"%s\"}".formatted(field), RequestTarget.body(field));
            }
        }
        for (String field : List.of("name", "title")) {
            run.add("error", "Unexpected response code: 500", 500, "SqlInjectionInStringFields", "POST", "/a",
                    "Unexpected 500", "{\"error\":\"PSQLException\",\"message\":\"syntax error at or near \\\"'%s\\\"\",\"trace\":\"at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2725)\"}".formatted(field),
                    RequestTarget.body(field));
        }
        return run.tests;
    }

    static List<CatsTestCaseSummary> summaries(List<CatsTestCase> tests) {
        return tests.stream().map(CatsTestCaseSummary::fromCatsTestCase).toList();
    }

    private CatsTestCase add(String result, String reason, int status, String fuzzer, String method, String path, String details,
                             String body, RequestTarget... fields) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setTestId("Test " + number.incrementAndGet());
        testCase.setScenario("Scenario of " + fuzzer);
        testCase.setResult(result);
        testCase.setResultReason(reason);
        testCase.setResultDetails(details);
        testCase.setFuzzer(fuzzer);
        testCase.setContractPath(path);
        testCase.setRequest(CatsRequest.builder().httpMethod(method).url("http://localhost:8080" + path).payload("{\"name\":\"<script>\"}").build());
        testCase.setResponse(CatsResponse.builder().responseCode(status).responseTimeInMs(5).body(body).build());
        testCase.getRequestProvenance().addMutationTargets(List.of(fields));
        tests.add(testCase);
        return testCase;
    }
}
