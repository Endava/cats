package com.endava.cats.command.triage;

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
        Assertions.assertThat(report.clusters()).singleElement().satisfies(cluster -> {
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

        Assertions.assertThat(report.clusters()).extracting(RootCauseCluster::category)
                .containsExactly("ROUTE_NOT_FOUND_404", "UNRESOLVED_BODY_REFERENCE_404", "UNRESOLVED_PATH_PARAM_404");
        Assertions.assertThat(report.clusters().get(1).remediation()).contains("customerId");
        Assertions.assertThat(report.clusters().get(2).remediation()).contains("POST /stores/{storeId}/orders", "orderId");
    }

    @Test
    void shouldGiveFlagSpecificBypassRemediations(@TempDir Path dir) throws IOException {
        write(dir, 1, test("NumericStringsInNumericFields", "Should return [4XX]", "POST", "/a", 200, "{}", "{}", null));
        write(dir, 2, test("EnumCaseVariantFields", "Should return [4XX]", "POST", "/b", 200, "{}", "{}", null));
        write(dir, 3, test("InvalidValuesInEnumsFields", "Should return [4XX]", "POST", "/c", 200, "{}", "{}", null));

        Assertions.assertThat(triage(dir).clusters()).extracting(RootCauseCluster::remediation)
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

        Assertions.assertThat(triage(first).clusters()).extracting(RootCauseCluster::operation)
                .containsExactly("POST /beta", "GET /alpha", "GET /zeta")
                .isEqualTo(triage(second).clusters().stream().map(RootCauseCluster::operation).toList());
        Assertions.assertThat(triage(first).synthesizedCorrelationsYaml()).isEqualTo(triage(second).synthesizedCorrelationsYaml());
    }

    @Test
    void shouldReportUnfixedCrashesOnlyWhenNotFixed() {
        RootCauseCluster inconclusive = cluster("SERVER_CRASH_5XX", "INCONCLUSIVE");
        RootCauseCluster fixed = cluster("SERVER_CRASH_5XX", "FIXED");

        Assertions.assertThat(TriageEngine.hasUnfixedServerCrash(report(List.of(fixed)))).isFalse();
        Assertions.assertThat(TriageEngine.hasUnfixedServerCrash(report(List.of(fixed, inconclusive)))).isTrue();
    }

    private static RootCauseCluster cluster(String category, String status) {
        return new RootCauseCluster(category, 0, "GET /x", 500, 1, "d", "r", List.of(), List.of(), "", status, 200, null);
    }

    private static TriageReport report(List<RootCauseCluster> clusters) {
        return new TriageReport("r", 1, 1, 0, clusters.size(), 0, clusters, List.of(), "", "", "");
    }

    private static List<String> categories(Path dir) throws IOException {
        return triage(dir).clusters().stream().map(RootCauseCluster::category).toList();
    }

    private static TriageReport triage(Path dir) throws IOException {
        return new TriageEngine(OPTIONS).triage(dir, null);
    }

    private static void write(Path dir, int number, String json) throws IOException {
        Files.writeString(dir.resolve("Test" + number + ".json"), json.replace("\"testId\": \"T\"", "\"testId\": \"Test " + number + "\""));
    }

    private static String test(String fuzzer, String expected, String method, String path, int code, String payload, String body, String extra) {
        return """
                {"testId": "T", "result": "error", "fuzzer": "%s", "expectedResult": "%s", "contractPath": "%s",
                 "request": {"httpMethod": "%s", "url": "http://localhost:8080%s", "payload": "%s"},
                 "response": {"responseCode": %d, "jsonBody": "%s"}%s}
                """.formatted(fuzzer, expected, path, method, path, payload, code, body, extra == null ? "" : ", " + extra);
    }
}
