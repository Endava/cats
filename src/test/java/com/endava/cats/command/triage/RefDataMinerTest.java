package com.endava.cats.command.triage;

import com.endava.cats.model.RequestTarget;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class RefDataMinerTest {

    @Test
    void shouldMapCreatedIdentifiersToRealContractPathParameters() {
        RefDataMiner miner = new RefDataMiner(Set.of("/orders", "/orders/{orderId}", "/orders/{orderId}/items/{itemId}"));
        miner.observe(exchange(1, "POST", "/orders", "http://localhost/orders", 201, "{\"id\":9001,\"valid\":true,\"activeId\":true,\"customerId\":\"c-1\"}"));

        Assertions.assertThat(miner.mined()).containsOnlyKeys("/orders/{orderId}");
        Assertions.assertThat(miner.mined().get("/orders/{orderId}")).containsOnlyKeys("orderId");
        Assertions.assertThat(miner.renderYaml()).contains("\"/orders/{orderId}\":", "orderId: 9001").doesNotContain("{id}", "valid", "activeId");
    }

    @Test
    void shouldMinePathParametersAndExcludeDeletedFuzzedAndBypassedValues() {
        RefDataMiner miner = new RefDataMiner(Set.of("/orders/{orderId}", "/orders/{orderId}/items"));
        miner.observe(exchange(1, "GET", "/orders/{orderId}", "http://localhost/api/orders/o-1", 200, "{}"));
        miner.observe(exchange(2, "GET", "/orders/{orderId}/items", "http://localhost/api/orders/o-2/items", 200, "{}"));
        miner.observe(exchange(3, "DELETE", "/orders/{orderId}", "http://localhost/api/orders/o-2", 204, ""));
        miner.observe(exchange(4, "GET", "/orders/{orderId}", "http://localhost/api/orders/o-3", 500, "{}"));

        Assertions.assertThat(miner.mined()).isEqualTo(Map.of("/orders/{orderId}", Map.of("orderId", "o-1")));
    }

    @Test
    void shouldIgnorePathValuesSentByPathFuzzersOrAcceptedNegativeTests() {
        RefDataMiner miner = new RefDataMiner(Set.of("/orders/{orderId}"));
        TriageTestCase fuzzedPath = exchange(1, "GET", "/orders/{orderId}", "http://localhost/orders/x1", 200, "{}");
        fuzzedPath.testCase().getRequestProvenance().addMutationTargets(List.of(RequestTarget.path("orderId")));
        TriageTestCase bypass = TriageFixtures.testCase(2, """
                {"result": "error", "expectedResult": "Should return [4XX]", "contractPath": "/orders/{orderId}",
                 "request": {"httpMethod": "GET", "url": "http://localhost/orders/AAAAAAAAAA"}, "response": {"responseCode": 200}}
                """);
        miner.observe(fuzzedPath);
        miner.observe(bypass);

        Assertions.assertThat(miner.mined()).isEmpty();
        Assertions.assertThat(miner.renderYaml()).contains("No 2XX path parameters");
    }

    @ParameterizedTest
    @CsvSource({"id,true", "UUID,true", "orderId,true", "order_id,true", "orderID,true", "valid,false", "paid,false", "android,false"})
    void shouldRecognizeIdentifierKeys(String key, boolean expected) {
        Assertions.assertThat(RefDataMiner.isIdKey(key)).isEqualTo(expected);
    }

    @Test
    void shouldNotExtractParametersFromMalformedOrShortUrls() {
        Assertions.assertThat(RefDataMiner.extractPathParams("/orders/{orderId}", "not a url")).isEmpty();
        Assertions.assertThat(RefDataMiner.extractPathParams("/a/b/{c}", "http://localhost/c")).isEmpty();
        Assertions.assertThat(RefDataMiner.extractPathParams("/orders", "http://localhost/orders")).isEmpty();
    }

    private static TriageTestCase exchange(int number, String method, String path, String url, int code, String body) {
        return TriageFixtures.testCase(number, """
                {"result": "success", "fuzzer": "HappyPath", "contractPath": "%s",
                 "request": {"httpMethod": "%s", "url": "%s"}, "response": {"responseCode": %d, "jsonBody": %s}}
                """.formatted(path, method, url, code, body.isEmpty() ? "null" : body));
    }
}
