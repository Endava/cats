package com.endava.cats.command.triage;

import com.endava.cats.model.ProblemKind;
import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@QuarkusTest
class TriageEngineTest {
    private static final TriageOptions OPTIONS = new TriageOptions(false, false, null, Map.of(), _ -> null);

    @Test
    void shouldClassifyCrashWithCorrelatedHeaderAsServerCrash(@TempDir Path dir) throws IOException {
        write(dir, 1, test("DummyContentTypeHeaders", "Should return [4XX]", "POST", "/orders", 500, "{}", "{\\\"message\\\":\\\"NPE\\\"}",
                "\"requestProvenance\": {\"runtimeCorrelations\": [{\"sourceMethod\": \"POST\", \"sourcePath\": \"/orders\","
                        + " \"sourceLocation\": \"response.headers.ETag\", \"target\": {\"location\": \"HEADER\", \"name\": \"If-Match\"}, \"value\": \"1\"}]}"));

        Assertions.assertThat(categories(dir)).containsExactly("SERVER_CRASH_5XX");
    }

    @Test
    void shouldRecommendRefDataWhenServerRejectsValueTheFuzzerMutates(@TempDir Path dir) throws IOException {
        write(dir, 1, test("MinLengthExactValuesInStringFields", "Should return [2XX]", "POST", "/watchlists", 422,
                "{\\\"name\\\":\\\"B\\\",\\\"type\\\":\\\"COUNTRY\\\"}", "{\\\"message\\\":\\\"Watchlist with name 'B' already exists\\\"}",
                "\"requestProvenance\": {\"mutationTargets\": [{\"location\": \"BODY\", \"name\": \"name\"}]}"));

        TriageReport report = triage(dir);

        Assertions.assertThat(report.synthesizedCorrelations()).isEmpty();
        Assertions.assertThat(occurrences(report)).singleElement().satisfies(cluster -> {
            Assertions.assertThat(cluster.category()).isEqualTo("FIELD_DOMAIN_CONSTRAINT_4XX");
            Assertions.assertThat(cluster.remediation()).contains("--refData", "\"name\"", "\"/watchlists\"", "--correlations cannot override");
        });
    }

    @Test
    void shouldDistinguishMissingRoutesBodyReferencesAndUnresolvedPathParameters(@TempDir Path dir) throws IOException {
        write(dir, 1, test("HappyPath", "Should return [2XX]", "GET", "/reports", 404, "", "{}", null));
        write(dir, 2, test("HappyPath", "Should return [2XX]", "POST", "/orders", 404,
                "{\\\"customerId\\\":\\\"c-404\\\",\\\"note\\\":\\\"x\\\"}", "{\\\"message\\\":\\\"Customer not found\\\"}", null));
        write(dir, 3, test("HappyPath", "Should return [2XX]", "GET", "/stores/{storeId}/orders/{orderId}", 404, "", "{}", null));

        TriageReport report = triage(dir);

        Assertions.assertThat(occurrences(report)).extracting(TriageOccurrence::category)
                .containsExactlyInAnyOrder("ROUTE_NOT_FOUND_404", "UNRESOLVED_BODY_REFERENCE_404", "UNRESOLVED_PATH_PARAM_404");
        Assertions.assertThat(occurrence(report, "UNRESOLVED_BODY_REFERENCE_404").remediation()).contains("customerId");
        Assertions.assertThat(occurrence(report, "UNRESOLVED_PATH_PARAM_404").remediation()).contains("POST /stores/{storeId}/orders", "orderId");
    }

    @Test
    void shouldGiveFlagSpecificBypassRemediations(@TempDir Path dir) throws IOException {
        write(dir, 1, test("NumericStringsInNumericFields", "Should return [4XX]", "POST", "/a", 200, "{}", "{}", null));
        write(dir, 2, test("EnumCaseVariantFields", "Should return [4XX]", "POST", "/b", 200, "{}", "{}", null));
        write(dir, 3, test("InvalidValuesInEnumsFields", "Should return [4XX]", "POST", "/c", 200, "{}", "{}", null));

        Assertions.assertThat(occurrences(triage(dir)).stream().sorted(java.util.Comparator.comparing(TriageOccurrence::operation)).toList())
                .extracting(TriageOccurrence::remediation)
                .satisfiesExactly(
                        remediation -> Assertions.assertThat(remediation).contains("--no-strictTypes"),
                        remediation -> Assertions.assertThat(remediation).contains("--no-allowDifferentCaseEnumValues"),
                        remediation -> Assertions.assertThat(remediation).contains("--allowInvalidEnumValues"));
    }

    @Test
    void shouldProduceTheSameClustersRegardlessOfTestNumbering(@TempDir Path dir) throws IOException {
        Path first = Files.createDirectories(dir.resolve("first"));
        Path second = Files.createDirectories(dir.resolve("second"));
        List<String> tests = List.of(
                test("HappyPath", "Should return [2XX]", "GET", "/zeta", 401, "", "{}", null),
                test("HappyPath", "Should return [2XX]", "GET", "/alpha", 403, "", "{}", null),
                test("HappyPath", "Should return [2XX]", "POST", "/beta", 422, "{\\\"planType\\\":\\\"RECURRING\\\"}",
                        "{\\\"message\\\":\\\"'billingSchedule' must not be null\\\"}", null));
        for (int i = 0; i < tests.size(); i++) {
            write(first, i + 1, tests.get(i));
            write(second, tests.size() - i, tests.get(i));
        }

        Assertions.assertThat(occurrences(triage(first))).extracting(TriageOccurrence::operation)
                .containsExactlyInAnyOrder("POST /beta", "GET /alpha", "GET /zeta")
                .isEqualTo(occurrences(triage(second)).stream().map(TriageOccurrence::operation).toList());
        Assertions.assertThat(triage(first).synthesizedCorrelationsYaml()).isEqualTo(triage(second).synthesizedCorrelationsYaml());
    }

    @Test
    void shouldClusterCrashesByErrorTemplateMaskingEchoedPathValues(@TempDir Path dir) throws IOException {
        write(dir, 1, test("HappyPath", "Should return [2XX]", "GET", "/accounts/0xa14f5", 500, "",
                "{\\\"message\\\":\\\"For input string: \\\\\\\"a14f5\\\\\\\" under radix 16\\\"}", null).replace("\"/accounts/0xa14f5\"", "\"/accounts/{id}\""));
        write(dir, 2, test("HappyPath", "Should return [2XX]", "GET", "/accounts/0xbb77", 500, "",
                "{\\\"message\\\":\\\"For input string: \\\\\\\"bb77\\\\\\\" under radix 16\\\"}", null).replace("\"/accounts/0xbb77\"", "\"/accounts/{id}\""));
        write(dir, 3, test("HappyPath", "Should return [2XX]", "GET", "/accounts/{id}", 500, "",
                "{\\\"message\\\":\\\"Cannot invoke \\\\\\\"String.length()\\\\\\\" because \\\\\\\"val\\\\\\\" is null\\\"}", null));

        TriageReport report = triage(dir);

        Assertions.assertThat(report.problems()).extracting(TriageProblem::testCount).containsExactly(2, 1);
        Assertions.assertThat(report.problems().getFirst().errorTemplate()).isEqualTo("For input string: \"<INPUT>\" under radix NUM");
        Assertions.assertThat(report.problems()).extracting(TriageProblem::errorTemplateId).doesNotHaveDuplicates().allMatch(id -> id.length() == 8);
        Assertions.assertThat(occurrences(report)).extracting(TriageOccurrence::category).containsOnly("SERVER_CRASH_5XX");
    }

    @Test
    void shouldTriageReportsContainingLinterResultsWithoutAnHttpMethod(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Test1.json"), """
                {"testId": "Test 1", "result": "error", "fuzzer": "UnusedComponentsLinter", "contractPath": "/pets",
                 "request": {"httpMethod": "N/A", "url": "####", "payload": "{}"},
                 "response": {"responseCode": 999, "jsonBody": "{}"}}
                """);

        Assertions.assertThat(occurrences(triage(dir))).singleElement()
                .satisfies(cluster -> Assertions.assertThat(cluster.curlReproducer()).isEmpty());
    }

    @Test
    void shouldAssignSameAsBaselineAttributionWithoutVerifyWhenSeedRequestFailedSameWay(@TempDir Path dir) throws IOException {
        write(dir, 3, test("HappyPath", "Should return [2XX]", "POST", "/orders", 500,
                "{\\\"customerId\\\":\\\"c-1\\\"}", "{\\\"message\\\":\\\"Customer c-1 does not exist\\\"}", null));
        write(dir, 12, test("RemoveFields", "Should return [4XX]", "POST", "/orders", 500,
                "{\\\"customerId\\\":\\\"c-1\\\"}", "{\\\"message\\\":\\\"Customer c-1 does not exist\\\"}", null));

        TriageReport report = triage(dir);

        Assertions.assertThat(occurrences(report)).singleElement().satisfies(cluster -> {
            Assertions.assertThat(cluster.attribution()).isEqualTo(TriageOccurrence.SAME_AS_BASELINE);
            Assertions.assertThat(cluster.remediation())
                    .contains("Same error as the unmodified request (Test 3, HappyPath)")
                    .contains("fix the HappyPath request for POST /orders first");
        });
    }

    @Test
    void shouldReportUnfixedCrashesOnlyWhenAServerErrorOccurrenceIsNotFixed() {
        TriageProblem fixedCrash = problem(ProblemKind.SERVER_ERROR, "FIXED", "FIXED");
        TriageProblem unfixedCrash = problem(ProblemKind.SERVER_ERROR, "FIXED", "INCONCLUSIVE");
        TriageProblem unfixedRejection = problem(ProblemKind.ERROR_MESSAGE, "STILL_FAILING");

        Assertions.assertThat(TriageEngine.hasUnfixedServerCrash(report(List.of(fixedCrash, unfixedRejection)))).isFalse();
        Assertions.assertThat(TriageEngine.hasUnfixedServerCrash(report(List.of(fixedCrash, unfixedCrash)))).isTrue();
    }

    @Test
    void shouldTriageWarningsByDefaultAndErrorsOnlyOnRequest(@TempDir Path dir) throws IOException {
        write(dir, 1, test("HappyPath", "Should return [2XX]", "POST", "/orders", 500, "{}", "{\\\"message\\\":\\\"NPE\\\"}", null));
        write(dir, 2, test("RemoveFields", "Should return [4XX]", "POST", "/orders", 201, "{}", "{}", null).replace("\"result\": \"error\"", "\"result\": \"warn\""));

        TriageReport all = triage(dir);
        TriageReport errorsOnly = new TriageEngine(new TriageOptions(true, false, null, Map.of(), _ -> null)).triage(dir, null);

        Assertions.assertThat(all.problems()).extracting(TriageProblem::kind).containsExactly(ProblemKind.SERVER_ERROR, ProblemKind.ACCEPTED_REQUEST);
        Assertions.assertThat(errorsOnly.problems()).extracting(TriageProblem::kind).containsExactly(ProblemKind.SERVER_ERROR);
        Assertions.assertThat(all.totalOccurrences()).isEqualTo(2);
        Assertions.assertThat(all.problemKeyVersion()).isEqualTo(1);
    }

    @Test
    void shouldUseThePersistedServerMessageForHtmlBodiesInsteadOfReMiningThem(@TempDir Path dir) throws IOException {
        String tomcat = "<!doctype html><html><head><title>HTTP Status 400 – Bad Request</title><style>body {font-family:Tahoma}</style></head>"
                + "<body><h1>HTTP Status 400 – Bad Request</h1></body></html>";
        for (int number = 1; number <= 3; number++) {
            Files.writeString(dir.resolve("Test%d.json".formatted(number)), JsonUtils.GSON.toJson(Map.of("testId", "Test " + number, "result", "error",
                    "resultReason", "Unexpected response code: 400", "fuzzer", "ControlCharsInStringFields", "contractPath", "/op" + number,
                    "request", Map.of("httpMethod", "POST", "url", "http://localhost/op" + number, "payload", "{}"),
                    "response", Map.of("responseCode", 400, "jsonBody", Map.of("notAJson", tomcat)))));
        }
        Files.writeString(dir.resolve(TriageEngine.SUMMARY_FILE), JsonUtils.GSON.toJson(summaryWithTemplate("cafe0400", "HTTP Status NUM – Bad Request", 3)));

        TriageReport report = triage(dir);

        Assertions.assertThat(report.problems()).singleElement().satisfies(problem -> {
            Assertions.assertThat(problem.errorTemplateId()).isEqualTo("cafe0400");
            Assertions.assertThat(problem.errorTemplate()).isEqualTo("HTTP Status NUM – Bad Request");
            Assertions.assertThat(problem.occurrences()).hasSize(3).allMatch(occurrence -> occurrence.testCount() == 1);
        });
    }

    @Test
    void shouldRejectReportsWithoutProblems(@TempDir Path dir) throws IOException {
        Assertions.assertThatThrownBy(() -> new TriageEngine(OPTIONS).triage(dir, null)).isInstanceOf(UnsupportedReportException.class)
                .hasMessageContaining("cats-summary-report.json");
        Files.writeString(dir.resolve(TriageEngine.SUMMARY_FILE), "{\"testCases\": []}");

        Assertions.assertThatThrownBy(() -> new TriageEngine(OPTIONS).triage(dir, null)).isInstanceOf(UnsupportedReportException.class)
                .hasMessageContaining("problemKeyVersion 0, expected 1").hasMessageContaining("re-run");
    }

    private static Map<String, Object> summaryWithTemplate(String templateId, String template, int tests) {
        List<Map<String, Object>> testCases = java.util.stream.IntStream.rangeClosed(1, tests).mapToObj(number -> Map.<String, Object>of("id", "Test " + number,
                "result", "error", "resultReason", "Unexpected response code: 400", "httpResponseCode", 400, "httpMethod", "post",
                "path", "/op" + number, "errorTemplateId", templateId, "problemId", "0badc0de")).toList();
        List<Map<String, Object>> occurrences = java.util.stream.IntStream.rangeClosed(1, tests).mapToObj(number -> Map.<String, Object>of(
                "operation", "POST /op" + number, "fields", List.of(), "testIds", List.of("Test " + number), "statuses", List.of(400),
                "fuzzers", List.of("ControlCharsInStringFields"))).toList();
        return Map.of("testCases", testCases, "problemKeyVersion", 1, "problems", List.of(Map.of("id", "0badc0de", "kind", "ERROR_MESSAGE",
                "title", "Request rejected (HTTP 400): " + template, "level", "error", "reasons", List.of("Unexpected response code: 400"),
                "testCount", tests, "errorTemplateId", templateId, "errorTemplate", template, "occurrences", occurrences)));
    }

    private static TriageProblem problem(ProblemKind kind, String... statuses) {
        List<TriageOccurrence> occurrences = java.util.Arrays.stream(statuses)
                .map(status -> TriageOccurrence.builder().operation("GET /x").statusCodes(List.of(500)).category("SERVER_CRASH_5XX")
                        .fuzzers(List.of()).fields(List.of()).sampleTestIds(List.of()).curlReproducer("").verificationStatus(status).build())
                .toList();
        return new TriageProblem("1a2b3c4d", kind, "t", "error", List.of(), statuses.length, null, null, occurrences);
    }

    private static TriageReport report(List<TriageProblem> problems) {
        return new TriageReport("r", 1, 1, 1, 0, problems.size(), problems.size(), 0, problems, List.of(), "", "", "");
    }

    private static List<TriageOccurrence> occurrences(TriageReport report) {
        return report.problems().stream().flatMap(problem -> problem.occurrences().stream()).toList();
    }

    private static TriageOccurrence occurrence(TriageReport report, String category) {
        return occurrences(report).stream().filter(occurrence -> category.equals(occurrence.category())).findFirst().orElseThrow();
    }

    private static List<String> categories(Path dir) throws IOException {
        return occurrences(triage(dir)).stream().map(TriageOccurrence::category).toList();
    }

    private static TriageReport triage(Path dir) throws IOException {
        if (!Files.exists(dir.resolve(TriageEngine.SUMMARY_FILE))) {
            TriageReportFixtures.writeSummary(dir);
        }
        return new TriageEngine(OPTIONS).triage(dir, null);
    }

    private static void write(Path dir, int number, String json) throws IOException {
        Files.writeString(dir.resolve("Test" + number + ".json"), json.replace("\"testId\": \"T\"", "\"testId\": \"Test " + number + "\""));
    }

    private static String test(String fuzzer, String expected, String method, String path, int code, String payload, String body, String extra) {
        return """
                {"testId": "T", "result": "error", "resultReason": "Unexpected response code: %d", "fuzzer": "%s", "expectedResult": "%s", "contractPath": "%s",
                 "request": {"httpMethod": "%s", "url": "http://localhost:8080%s", "payload": "%s"},
                 "response": {"responseCode": %d, "jsonBody": "%s"}%s}
                """.formatted(code, fuzzer, expected, path, method, path, payload, code, body, extra == null ? "" : ", " + extra);
    }
}
