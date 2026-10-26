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
                {"counter":{"metric":"COUNT","metricField":"amount"},"transactionFacts":{"country":"US","amount":150,"currency":"EUR"}}
                """;
        List<FieldCorrelationRule> rules = List.of(
                new FieldCorrelationRule(List.of("/billing-plans*"), "counter.metric == 'COUNT'",
                        Map.of(), List.of("counter.metricField")),
                new FieldCorrelationRule(List.of("*"), "transactionFacts.country = 'US' & transactionFacts.amount > 100",
                        Map.of("transactionFacts.currency", "${preferredCurrency}"), List.of())
        );

        String updated = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/billing-plans", payload, rules, Map.of("preferredCurrency", "USD"));

        Assertions.assertThat(updated)
                .doesNotContain("metricField")
                .contains("\"currency\":\"USD\"");
    }

    @Test
    void shouldSkipRequestCorrelationWhenPathOrConditionDoesNotMatch() {
        String payload = "{\"counter\":{\"metric\":\"SUM\",\"metricField\":\"amount\"}}";
        List<FieldCorrelationRule> rules = List.of(
                new FieldCorrelationRule(List.of("/other*"), "counter.metric == 'SUM'",
                        Map.of(), List.of("counter.metricField")),
                new FieldCorrelationRule(List.of("/billing-plans*"), "counter.metric == 'COUNT'",
                        Map.of(), List.of("counter.metricField"))
        );

        String updated = FieldCorrelationEvaluator.applyRequestCorrelations("/billing-plans", payload, rules, Map.of());
        Assertions.assertThat(updated).contains("metricField");
    }

    @Test
    void shouldEvaluateResponseWhereCorrelationsWithSingleAndDoubleAmpersandAndPipe() {
        String requestJson = "{\"partyId\":\"party_1\",\"email\":\"john@email.com\"}";
        String responseBody = """
                {"totalScore":80,"decision":"FLAGGED","decisionMetadata":{"threshold":50},"matchedItems":[{"id":"r1"}],"periodStart":"2026-01-01","periodEnd":"2026-01-02"}
                """;
        CatsResponse response = CatsResponse.builder()
                .responseCode(200)
                .responseTimeInMs(45)
                .body(responseBody)
                .build();

        List<String> passingWhere = List.of(
                "totalScore >= decisionMetadata.threshold && decision == 'FLAGGED'",
                "time > 10 & $request.email = 'john@email.com'",
                "responseTimeMs < 500 & status == 200",
                "size(matchedItems) > 0 & decision is not null & missingField is null",
                "decision contains 'FLAG' & decision matches 'FLAG.*'",
                "periodEnd > periodStart | totalScore < 0"
        );

        Optional<String> result = FieldCorrelationEvaluator.evaluateThatExpectations(
                passingWhere, requestJson, response, Map.of());
        Assertions.assertThat(result).isEmpty();

        Optional<String> failingResult = FieldCorrelationEvaluator.evaluateThatExpectations(
                List.of("totalScore < decisionMetadata.threshold"), requestJson, response, Map.of());
        Assertions.assertThat(failingResult)
                .isPresent()
                .get().asString().contains("totalScore < decisionMetadata.threshold");
    }

    @Test
    void shouldProtectFuzzedFieldsAndInsertMissingNestedFieldsAndParseCorrelationFile(@TempDir Path tempDir) throws Exception {
        String yaml = """
                correlate:
                  - paths: ["/billing-plans*"]
                    when: "counter.metric == 'SUM'"
                    then:
                      set:
                        counter.metricField: "amount"
                  - paths: ["/billing-plans*"]
                    when: "expression.type == 'CONDITION' & expression.field is null"
                    then:
                      set:
                        expression.field: "country"
                """;
        File corrFile = tempDir.resolve("correlations.yml").toFile();
        Files.writeString(corrFile.toPath(), yaml);
        List<FieldCorrelationRule> rules = FunctionalFlowParser.parseCorrelationFile(corrFile);
        Assertions.assertThat(rules).hasSize(2);

        String missingFieldsPayload = "{\"counter\":{\"metric\":\"SUM\"},\"expression\":{\"type\":\"CONDITION\"}}";
        String enriched = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/billing-plans", missingFieldsPayload, rules, Map.of(), Set.of());
        Assertions.assertThat(enriched)
                .contains("\"metricField\":\"amount\"")
                .contains("\"field\":\"country\"");

        String fuzzedPayload = "{\"counter\":{\"metric\":\"SUM\",\"metricField\":\"FUZZED_VAL\"},\"expression\":{\"type\":\"CONDITION\"}}";
        String preserved = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/billing-plans", fuzzedPayload, rules, Map.of(), Set.of("counter#metricField"));
        Assertions.assertThat(preserved).contains("\"metricField\":\"FUZZED_VAL\"");

        String zwspPayload = "{\"counter\":{\"metric\":\"SUM\",\"metricField\":\"am\\u200bount\"},\"expression\":{\"type\":\"CONDITION\"}}";
        String merged = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/billing-plans", zwspPayload, rules, Map.of(), Set.of("counter#metricField"));
        Assertions.assertThat(merged).contains("amo\u200bunt");
    }

    @Test
    void shouldExpandWildcardArrayCorrelationsPerElement(@TempDir Path tempDir) throws Exception {
        String yaml = """
                correlations:
                  - paths: ["/billing-plans*"]
                    when: "schedules[*].metric == 'COUNT'"
                    then:
                      omit: ["schedules[*].metricField"]
                  - paths: ["/billing-plans*"]
                    when: "schedules[*].metric == 'DISTINCT_COUNT'"
                    then:
                      set:
                        schedules[*].metricField: "country"
                  - paths: ["/billing-plans*"]
                    when: "expression.type == 'CONDITION'"
                    then:
                      set:
                        expression.operator: "EQ"
                        name: "Plan-Updated"
                """;
        File corrFile = tempDir.resolve("wildcard-correlations.yml").toFile();
        Files.writeString(corrFile.toPath(), yaml);
        List<FieldCorrelationRule> rules = FunctionalFlowParser.parseCorrelationFile(corrFile);
        String payload = """
                {"expression":{"type":"CONDITION"},"planType":"RECURRING","name":"Plan-Old","schedules":[{"name":"c1","metricField":"amount","dimension":"region","metric":"COUNT"},{"name":"c2","metricField":"RANDOM","dimension":"ipv4","metric":"DISTINCT_COUNT"}]}
                """;
        String updated = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/billing-plans", payload, rules, Map.of(), Set.of());
        Assertions.assertThat(updated)
                .doesNotContain("\"metricField\":\"amount\"")
                .contains("\"metricField\":\"country\"")
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
