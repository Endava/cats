package com.endava.cats.fuzzer.special;

import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FieldCorrelationRule;
import com.endava.cats.model.CatsResponse;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@QuarkusTest
class FieldCorrelationEvaluatorTest {

    @Test
    void shouldApplyRequestCorrelationOmitAndSetWhenConditionHolds() {
        String payload = """
                {"pricing":{"tier":"FLAT","unitRate":"10"},"shipping":{"country":"US","weight":150,"currency":"EUR"}}
                """;
        List<FieldCorrelationRule> rules = List.of(
                new FieldCorrelationRule(List.of("/billing-plans*"), "pricing.tier == 'FLAT'",
                        Map.of(), List.of("pricing.unitRate")),
                new FieldCorrelationRule(List.of("*"), "shipping.country = 'US' & shipping.weight > 100",
                        Map.of("shipping.currency", "${preferredCurrency}"), List.of())
        );

        String updated = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/billing-plans", payload, rules, Map.of("preferredCurrency", "USD"));

        Assertions.assertThat(updated)
                .doesNotContain("unitRate")
                .contains("\"currency\":\"USD\"");
    }

    @Test
    void shouldSkipRequestCorrelationWhenPathOrConditionDoesNotMatch() {
        String payload = "{\"pricing\":{\"tier\":\"TIERED\",\"unitRate\":\"10\"}}";
        List<FieldCorrelationRule> rules = List.of(
                new FieldCorrelationRule(List.of("/other*"), "pricing.tier == 'TIERED'",
                        Map.of(), List.of("pricing.unitRate")),
                new FieldCorrelationRule(List.of("/billing-plans*"), "pricing.tier == 'FLAT'",
                        Map.of(), List.of("pricing.unitRate"))
        );

        String updated = FieldCorrelationEvaluator.applyRequestCorrelations("/billing-plans", payload, rules, Map.of());
        Assertions.assertThat(updated).contains("unitRate");
    }

    @Test
    void shouldApplyRequestCorrelationsToRootJsonArrayElements() {
        String payload = "[{\"delivery\":{\"mode\":\"eXpReSs\"}},{\"delivery\":{\"mode\":\"STANDARD\"},\"trackingCode\":\"TRK-1\"},\"scalar\"]";
        List<FieldCorrelationRule> rules = List.of(
                new FieldCorrelationRule(List.of("/shipments"), "delivery.mode == 'EXPRESS'",
                        Map.of("trackingCode", "EXP-900"), List.of()),
                new FieldCorrelationRule(List.of("/shipments"), "delivery.mode != 'EXPRESS'",
                        Map.of(), List.of("trackingCode"))
        );

        String updated = FieldCorrelationEvaluator.applyRequestCorrelations("/shipments", payload, rules, Map.of());

        Assertions.assertThat(updated)
                .contains("\"trackingCode\":\"EXP-900\"")
                .doesNotContain("\"trackingCode\":\"TRK-1\"");
    }

    @Test
    void shouldEvaluateResponseWhereCorrelationsWithSingleAndDoubleAmpersandAndPipe() {
        String requestJson = "{\"customerId\":\"cust_1\",\"email\":\"john@email.com\"}";
        String responseBody = """
                {"orderTotal":80,"state":"APPROVED","limits":{"minAmount":50},"lineItems":[{"id":"r1"}],"startDate":"2026-01-01","endDate":"2026-01-02"}
                """;
        CatsResponse response = CatsResponse.builder()
                .responseCode(200)
                .responseTimeInMs(45)
                .body(responseBody)
                .build();

        List<String> passingWhere = List.of(
                "orderTotal >= limits.minAmount && state == 'APPROVED'",
                "time > 10 & $request.email = 'john@email.com'",
                "responseTimeMs < 500 & status == 200",
                "size(lineItems) > 0 & state is not null & missingField is null",
                "state contains 'APPROV' & state matches 'APPROV.*'",
                "endDate > startDate | orderTotal < 0"
        );

        Optional<String> result = FieldCorrelationEvaluator.evaluateThatExpectations(
                passingWhere, requestJson, response, Map.of());
        Assertions.assertThat(result).isEmpty();

        Optional<String> failingResult = FieldCorrelationEvaluator.evaluateThatExpectations(
                List.of("orderTotal < limits.minAmount"), requestJson, response, Map.of());
        Assertions.assertThat(failingResult)
                .isPresent()
                .get().asString().contains("orderTotal < limits.minAmount");
    }

    @Test
    void shouldProtectFuzzedFieldsAndInsertMissingNestedFieldsAndParseCorrelationFile(@TempDir Path tempDir) throws Exception {
        String yaml = """
                correlate:
                  - paths: ["/billing-plans*"]
                    when: "pricing.tier == 'TIERED'"
                    then:
                      set:
                        pricing.unitRate: "amount"
                  - paths: ["/billing-plans*"]
                    when: "filter.kind == 'SIMPLE' & filter.target is null"
                    then:
                      set:
                        filter.target: "region"
                """;
        File corrFile = tempDir.resolve("correlations.yml").toFile();
        Files.writeString(corrFile.toPath(), yaml);
        List<FieldCorrelationRule> rules = FunctionalFlowParser.parseCorrelationFile(corrFile);
        Assertions.assertThat(rules).hasSize(2);

        String missingFieldsPayload = "{\"pricing\":{\"tier\":\"TIERED\"},\"filter\":{\"kind\":\"SIMPLE\"}}";
        String enriched = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/billing-plans", missingFieldsPayload, rules, Map.of(), Set.of());
        Assertions.assertThat(enriched)
                .contains("\"unitRate\":\"amount\"")
                .contains("\"target\":\"region\"");

        String fuzzedPayload = "{\"pricing\":{\"tier\":\"TIERED\",\"unitRate\":\"FUZZED_VAL\"},\"filter\":{\"kind\":\"SIMPLE\"}}";
        String preserved = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/billing-plans", fuzzedPayload, rules, Map.of(), Set.of("pricing#unitRate"));
        Assertions.assertThat(preserved).contains("\"unitRate\":\"FUZZED_VAL\"");

        String zwspPayload = "{\"pricing\":{\"tier\":\"TIERED\",\"unitRate\":\"am\\u200bount\"},\"filter\":{\"kind\":\"SIMPLE\"}}";
        String merged = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/billing-plans", zwspPayload, rules, Map.of(), Set.of("pricing#unitRate"));
        Assertions.assertThat(merged).contains("amo\u200bunt");
    }

    @Test
    void shouldExpandWildcardArrayCorrelationsPerElement(@TempDir Path tempDir) throws Exception {
        String yaml = """
                correlations:
                  - paths: ["/billing-plans*"]
                    when: "tiers[*].mode == 'FIXED'"
                    then:
                      omit: ["tiers[*].unitField"]
                  - paths: ["/billing-plans*"]
                    when: "tiers[*].mode == 'METERED'"
                    then:
                      set:
                        tiers[*].unitField: "SeatCount"
                  - paths: ["/billing-plans*"]
                    when: "filter.kind == 'SIMPLE'"
                    then:
                      set:
                        filter.operator: "EQ"
                        name: "Plan-Updated"
                """;
        File corrFile = tempDir.resolve("wildcard-correlations.yml").toFile();
        Files.writeString(corrFile.toPath(), yaml);
        List<FieldCorrelationRule> rules = FunctionalFlowParser.parseCorrelationFile(corrFile);
        String payload = """
                {"filter":{"kind":"SIMPLE"},"planType":"RECURRING","name":"Plan-Old","tiers":[{"name":"t1","unitField":"hours","group":"region","mode":"FIXED"},{"name":"t2","unitField":"RANDOM","group":"zone","mode":"METERED"}]}
                """;
        String updated = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/billing-plans", payload, rules, Map.of(), Set.of());
        Assertions.assertThat(updated)
                .doesNotContain("\"unitField\":\"hours\"")
                .contains("\"unitField\":\"SeatCount\"")
                .contains("\"operator\":\"EQ\"")
                .doesNotContain("Plan-Old");
    }

    @Test
    void shouldEvaluateWhereExpectationsAndComplexBooleanExpressions() {
        CatsResponse response = CatsResponse.builder()
                .responseCode(201)
                .responseTimeInMs(12)
                .body("{\"id\":\"ord_1\",\"amount\":50,\"status\":\"ACTIVE\",\"active\":true,\"tags\":\"fast,priority\",\"items\":[1,2]}")
                .build();
        String reqJson = "{\"amount\":50,\"mode\":\"EXPRESS\"}";
        Map<String, String> vars = Map.of("expectedStatus", "ACTIVE");

        Assertions.assertThat(FieldCorrelationEvaluator.evaluateThatExpectations(
                List.of(
                        "$status == 201 && response.amount >= 50",
                        "response.status == '${expectedStatus}' || response.status == 'PENDING'",
                        "response.tags contains 'fast' && response.id matches 'ord_\\d+'",
                        "response.id is not null && response.missingField is null",
                        "response.missingField != 'SOMETHING'",
                        "responseTimeMs <= 100 && response.amount < 60 && response.amount > 10",
                        "size(items) == 2 && active"
                ),
                reqJson, response, vars)).isEmpty();

        String arrayItemPayload = "{\"items\":[{\"name\":\"old\"}]}";
        List<FieldCorrelationRule> itemRules = List.of(
                new FieldCorrelationRule(List.of("/orders"), "items[0].name == 'old'", Map.of("items[0].name", "new"), List.of()));
        String mutatedArray = FieldCorrelationEvaluator.applyRequestCorrelations("/orders", arrayItemPayload, itemRules, Map.of());
        Assertions.assertThat(mutatedArray).contains("\"name\":\"new\"");

        Assertions.assertThat(FieldCorrelationEvaluator.evaluateThatExpectations(
                List.of("response.amount > 100"), reqJson, response, vars))
                .isPresent()
                .get().asString().contains("response.amount > 100");
    }
}
