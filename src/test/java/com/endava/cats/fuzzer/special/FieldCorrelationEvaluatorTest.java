package com.endava.cats.fuzzer.special;

import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FieldCorrelationRule;
import com.endava.cats.model.CatsResponse;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

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
                new FieldCorrelationRule(List.of("/risk-rules*"), "counter.metric == 'COUNT'",
                        Map.of(), List.of("counter.metricField")),
                new FieldCorrelationRule(List.of("*"), "transactionFacts.country = 'US' & transactionFacts.amount > 100",
                        Map.of("transactionFacts.currency", "${preferredCurrency}"), List.of())
        );

        String updated = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/risk-rules", payload, rules, Map.of("preferredCurrency", "USD"));

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
                new FieldCorrelationRule(List.of("/risk-rules*"), "counter.metric == 'COUNT'",
                        Map.of(), List.of("counter.metricField"))
        );

        String updated = FieldCorrelationEvaluator.applyRequestCorrelations("/risk-rules", payload, rules, Map.of());
        Assertions.assertThat(updated).contains("metricField");
    }

    @Test
    void shouldEvaluateResponseWhereCorrelationsWithSingleAndDoubleAmpersandAndPipe() {
        String requestJson = "{\"partyId\":\"party_1\",\"email\":\"john@email.com\"}";
        String responseBody = """
                {"totalRiskScore":80,"decision":"POSSIBLE_FRAUD","decisionMetadata":{"threshold":50},"matchedRules":[{"id":"r1"}],"periodStart":"2026-01-01","periodEnd":"2026-01-02"}
                """;
        CatsResponse response = CatsResponse.builder()
                .responseCode(200)
                .responseTimeInMs(45)
                .body(responseBody)
                .build();

        List<String> passingWhere = List.of(
                "totalRiskScore >= decisionMetadata.threshold && decision == 'POSSIBLE_FRAUD'",
                "time > 10 & $request.email = 'john@email.com'",
                "responseTimeMs < 500 & status == 200",
                "size(matchedRules) > 0 & decision is not null & missingField is null",
                "decision contains 'FRAUD' & decision matches 'POSSIBLE_.*'",
                "periodEnd > periodStart | totalRiskScore < 0"
        );

        Optional<String> result = FieldCorrelationEvaluator.evaluateWhereExpectations(
                passingWhere, requestJson, response, Map.of());
        Assertions.assertThat(result).isEmpty();

        Optional<String> failingResult = FieldCorrelationEvaluator.evaluateWhereExpectations(
                List.of("totalRiskScore < decisionMetadata.threshold"), requestJson, response, Map.of());
        Assertions.assertThat(failingResult)
                .isPresent()
                .get().asString().contains("totalRiskScore < decisionMetadata.threshold");
    }

    @Test
    void shouldProtectFuzzedFieldsAndInsertMissingNestedFieldsAndParseCorrelationFile(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        String yaml = """
                correlate:
                  - paths: ["/risk-rules*"]
                    when: "counter.metric == 'SUM'"
                    then:
                      set:
                        counter.metricField: "amount"
                  - paths: ["/risk-rules*"]
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
                "/risk-rules", missingFieldsPayload, rules, Map.of(), Set.of());
        Assertions.assertThat(enriched)
                .contains("\"metricField\":\"amount\"")
                .contains("\"field\":\"country\"");

        String fuzzedPayload = "{\"counter\":{\"metric\":\"SUM\",\"metricField\":\"FUZZED_VAL\"},\"expression\":{\"type\":\"CONDITION\"}}";
        String preserved = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/risk-rules", fuzzedPayload, rules, Map.of(), Set.of("counter#metricField"));
        Assertions.assertThat(preserved).contains("\"metricField\":\"FUZZED_VAL\"");
    }
}
