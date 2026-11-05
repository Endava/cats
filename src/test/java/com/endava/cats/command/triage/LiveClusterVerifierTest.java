package com.endava.cats.command.triage;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

@QuarkusTest
class LiveClusterVerifierTest {
    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @ParameterizedTest
    @CsvSource({
            "SERVER_CRASH_5XX,500,Should return [4XX],400,FIXED",
            "SERVER_CRASH_5XX,500,Should return [4XX],502,STILL_FAILING",
            "SERVER_CRASH_5XX,500,Should return [4XX],401,INCONCLUSIVE",
            "SERVER_CRASH_5XX,500,Should return [4XX],404,INCONCLUSIVE",
            "UNRESOLVED_PATH_PARAM_404,404,Should return [2XX],404,STILL_FAILING",
            "AUTH_CONFIGURATION_GAP,401,Should return [2XX],401,STILL_FAILING",
            "CONTRACT_MISMATCH,409,'Should return [400, 422]',422,FIXED",
            "CONTRACT_MISMATCH,409,'Should return [400, 422]',409,STILL_FAILING",
            "VALIDATION_BYPASS_2XX,200,Should return [4XX],403,INCONCLUSIVE"})
    void shouldJudgeReplayOutcomes(String category, int originalCode, String expected, int replayCode, String verdict) {
        status.set(replayCode);

        LiveClusterVerifier.Verification verification = verifier(Map.of(), _ -> null)
                .verify(classification(category, true), testCase(originalCode, expected, "application/json"));

        Assertions.assertThat(verification.status()).isEqualTo(verdict);
    }

    @Test
    void shouldResolveMaskedHeadersFromTheEnvironment() {
        verifier(Map.of(), name -> "Authorization".equals(name) ? "Bearer real" : null)
                .verify(classification("SERVER_CRASH_5XX", true), testCase(500, "Should return [2XX]", "$$Authorization"));

        Assertions.assertThat(receivedAuthorization.get()).isEqualTo("Bearer real");
    }

    @Test
    void shouldBeInconclusiveWhenMaskedValuesAreMissingUnlessOverridden() {
        TriageTestCase masked = testCase(500, "Should return [2XX]", "$$Authorization");

        LiveClusterVerifier.Verification missing = verifier(Map.of(), _ -> null).verify(classification("SERVER_CRASH_5XX", true), masked);
        LiveClusterVerifier.Verification overridden = verifier(Map.of("authorization", "Bearer cli"), _ -> null)
                .verify(classification("SERVER_CRASH_5XX", true), masked);

        Assertions.assertThat(missing.status()).isEqualTo(LiveClusterVerifier.INCONCLUSIVE);
        Assertions.assertThat(missing.note()).contains("Authorization");
        Assertions.assertThat(overridden.status()).isEqualTo(LiveClusterVerifier.FIXED);
        Assertions.assertThat(receivedAuthorization.get()).isEqualTo("Bearer cli");
    }

    @Test
    void shouldNotReplayContractOnlyClustersOrUnreachableServers() {
        LiveClusterVerifier.Verification contractOnly = verifier(Map.of(), _ -> null)
                .verify(classification("CONTRACT_MISMATCH", false), testCase(204, "Should return [2XX]", "application/json"));
        LiveClusterVerifier.Verification unreachable = new LiveClusterVerifier("http://127.0.0.1:1", Map.of(), _ -> null)
                .verify(classification("SERVER_CRASH_5XX", true), testCase(500, "Should return [2XX]", "application/json"));

        Assertions.assertThat(contractOnly.status()).isEqualTo(LiveClusterVerifier.INCONCLUSIVE);
        Assertions.assertThat(unreachable.status()).isEqualTo(LiveClusterVerifier.INCONCLUSIVE);
        Assertions.assertThat(unreachable.statusCode()).isNull();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "http://localhost:8080/v1/orders,'',http://localhost:8080/v1/orders",
            "not-a-url,https://staging.example.com/api/,not-a-url",
            "http://localhost:8080/orders,https://staging.example.com/api/,https://staging.example.com/api/orders",
            "http://localhost:8080/api/orders,https://staging.example.com/api,https://staging.example.com/api/orders",
            "http://localhost:8080/orders?x=1,http://127.0.0.1:9000,http://127.0.0.1:9000/orders?x=1"})
    void shouldOverrideServerBaseUrl(String original, String override, String expected) {
        Assertions.assertThat(new LiveClusterVerifier(override, Map.of(), _ -> null).resolveTargetUrl(original)).isEqualTo(expected);
    }

    private LiveClusterVerifier verifier(Map<String, String> headers, UnaryOperator<String> environment) {
        return new LiveClusterVerifier("http://127.0.0.1:" + server.getAddress().getPort(), headers, environment);
    }

    private static ClusterClassification classification(String category, boolean liveVerifiable) {
        return new ClusterClassification(category, category, 0, "GET /orders", 500, "d", "r", liveVerifiable);
    }

    private static TriageTestCase testCase(int originalCode, String expected, String authorization) {
        return TriageFixtures.testCase(7, """
                {"testId": "Test 7", "result": "error", "expectedResult": "%s", "contractPath": "/orders/{id}",
                 "request": {"httpMethod": "GET", "url": "http://localhost:8080/orders/1", "sendBody": true, "payload": "{}",
                   "headers": [{"key": "Authorization", "value": "%s"}]},
                 "response": {"responseCode": %d}}
                """.formatted(expected, authorization, originalCode));
    }
}
