package com.endava.cats.fuzzer.special;

import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FieldCorrelationRule;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.util.JsonUtils;
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

        Set<String> condReq = FieldCorrelationEvaluator.collectRequiredFieldsFromCorrelations(
                "/billing-plans", missingFieldsPayload, rules);
        Assertions.assertThat(condReq).contains("pricing#unitRate", "filter#target");

        Set<String> batchCondReq = FieldCorrelationEvaluator.collectRequiredFieldsFromCorrelations(
                "/billing-plans", "[" + missingFieldsPayload + "]", rules);
        Assertions.assertThat(batchCondReq).contains("pricing#unitRate", "filter#target");

        List<FieldCorrelationRule> unconditionalRule = List.of(
                new FieldCorrelationRule(List.of("/tokens"), null, Map.of("suppliedToken", "TKN123"), List.of()));
        Assertions.assertThat(FieldCorrelationEvaluator.collectRequiredFieldsFromCorrelations(
                "/tokens", "{\"format\":{\"type\":\"ALPHANUMERIC\"}}", unconditionalRule)).isEmpty();

        List<FieldCorrelationRule> unquotedRhsRule = List.of(
                new FieldCorrelationRule(List.of("/tokens"), "format.type == SUPPLIED", Map.of("suppliedToken", "TKN123"), List.of()));
        Assertions.assertThat(FieldCorrelationEvaluator.collectRequiredFieldsFromCorrelations(
                "/tokens", "{\"format\":{\"type\":\"SUPPLIED\"}}", unquotedRhsRule)).containsExactly("suppliedToken");
        Assertions.assertThat(FieldCorrelationEvaluator.applyRequestCorrelations(
                "/tokens", "{\"format\":{\"type\":\"SUPPLIED\"}}", unquotedRhsRule, Map.of()))
                .contains("\"suppliedToken\":\"TKN123\"");

        List<FieldCorrelationRule> selfPresenceGuardRule = List.of(
                new FieldCorrelationRule(List.of("/risk-watchlists"), "name is not null", Map.of("name", "Blocked-123"), List.of()));
        Assertions.assertThat(FieldCorrelationEvaluator.collectRequiredFieldsFromCorrelations(
                "/risk-watchlists", "{\"name\":\"initial\"}", selfPresenceGuardRule)).isEmpty();
    }

    @Test
    void shouldParseGroupedRulesListAndPathKeyedCorrelationFormats(@TempDir Path tempDir) throws Exception {
        File groupedFile = tempDir.resolve("grouped-correlations.yml").toFile();
        Files.writeString(groupedFile.toPath(), """
                correlations:
                  - paths:
                      - "/orders"
                      - "/orders/batch"
                    rules:
                      - when: "format.type == SUPPLIED"
                        set:
                          suppliedToken: "TKN111"
                      - when: "format.type != SUPPLIED"
                        omit:
                          - suppliedToken
                """);
        List<FieldCorrelationRule> groupedRules = FunctionalFlowParser.parseCorrelationFile(groupedFile);
        Assertions.assertThat(groupedRules).hasSize(2);
        Assertions.assertThat(groupedRules.get(0).paths()).containsExactly("/orders", "/orders/batch");
        Assertions.assertThat(groupedRules.get(0).when()).isEqualTo("format.type == SUPPLIED");
        Assertions.assertThat(groupedRules.get(0).set()).containsEntry("suppliedToken", "TKN111");
        Assertions.assertThat(groupedRules.get(1).omit()).containsExactly("suppliedToken");

        File pathKeyedFile = tempDir.resolve("path-keyed-correlations.yml").toFile();
        Files.writeString(pathKeyedFile.toPath(), """
                all:
                  when: "mode == 'TEST'"
                  set:
                    dryRunFlag: true
                /orders:
                  - when: "format.type == SUPPLIED"
                    set:
                      suppliedToken: "TKN222"
                  - when: "format.type != SUPPLIED"
                    omit:
                      - suppliedToken
                """);
        List<FieldCorrelationRule> pathKeyedRules = FunctionalFlowParser.parseCorrelationFile(pathKeyedFile);
        Assertions.assertThat(pathKeyedRules).hasSize(3);
        Assertions.assertThat(pathKeyedRules.get(0).paths()).containsExactly("*");
        Assertions.assertThat(pathKeyedRules.get(1).paths()).containsExactly("/orders");
        Assertions.assertThat(pathKeyedRules.get(2).paths()).containsExactly("/orders");
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

    @Test
    void shouldHandleStepReferencesCollectionSizesWildcardOmitAndSpelExpressions() {
        Assertions.assertThat(FieldCorrelationEvaluator.applyRequestCorrelations("/p", "", List.of(), Map.of())).isEmpty();
        Assertions.assertThat(FieldCorrelationEvaluator.applyRequestCorrelations("/p", "not-json", List.of(), Map.of())).isEqualTo("not-json");
        Assertions.assertThat(FieldCorrelationEvaluator.normalizeFieldPath(null)).isEmpty();
        Assertions.assertThat(FieldCorrelationEvaluator.normalizeFieldPath("$.a.b")).isEqualTo("a#b");
        Assertions.assertThat(FieldCorrelationEvaluator.normalizeFieldPath("$#a.b")).isEqualTo("a#b");
        Assertions.assertThat(FieldCorrelationEvaluator.lookupJsonPath("", "a")).isEqualTo(JsonUtils.NOT_SET);
        Assertions.assertThat(FieldCorrelationEvaluator.lookupJsonPath("[{\"code\":\"X\"}]", "code")).isEqualTo("X");

        // Wildcard in omit and set, plus indexed delete and setOrInsertField with createMissingParents
        FieldCorrelationRule wildcardOmit = new FieldCorrelationRule(
                List.of("/orders"), null, Map.of("items[*].tag", "T"), List.of("items[*].secret"));
        String afterWildcard = FieldCorrelationEvaluator.applyRequestCorrelations(
                "/orders", "{\"items\":[{\"secret\":\"s1\"}]}", List.of(wildcardOmit), Map.of());
        Assertions.assertThat(afterWildcard).doesNotContain("secret").contains("\"tag\":\"T\"");

        FieldCorrelationRule wildcardSetOnly = new FieldCorrelationRule(
                List.of("/orders"), null, Map.of("items[*].added", 1), List.of());
        Assertions.assertThat(FieldCorrelationEvaluator.applyRequestCorrelations(
                "/orders", "{\"items\":[]}", List.of(wildcardSetOnly), Map.of())).isEqualTo("{\"items\":[]}");

        String insertedNested = FieldCorrelationEvaluator.setOrInsertField("{}", "a#b#c", "val", true);
        Assertions.assertThat(insertedNested).isEqualTo("{\"a\":{\"b\":{\"c\":\"val\"}}}");
        Assertions.assertThat(FieldCorrelationEvaluator.setOrInsertField("{}", "a[0]#b", "val", false)).isEqualTo("{}");

        Map<String, String> stepVars = Map.of(
                "prev.request", "{\"reqId\":\"r1\"}",
                "prev.response", "{\"respId\":\"p1\"}",
                "steps.s1.request", "{\"sReq\":\"sr1\"}",
                "steps.s1.response", "{\"sResp\":\"sp1\"}",
                "plainVar", "pv");
        CatsResponse resp = CatsResponse.builder().responseCode(200).responseTimeInMs(5).body("{\"arr\":[1,2,3],\"scalar\":\"v\"}").build();

        Assertions.assertThat(FieldCorrelationEvaluator.evaluateBooleanExpression(
                "", "{}", resp.getBody(), resp, stepVars)).isTrue();
        Assertions.assertThat(FieldCorrelationEvaluator.evaluateBooleanExpression(
                "prev.request.reqId == 'r1' & prev.response.respId == 'p1' & steps.s1.request.sReq == 'sr1' & steps.s1.response.sResp == 'sp1'",
                "{}", resp.getBody(), resp, stepVars)).isTrue();
        Assertions.assertThat(FieldCorrelationEvaluator.evaluateBooleanExpression(
                "${steps.s1.response.sResp} == 'sp1' & ${missingVar} == null & scalar != null & scalar notNull",
                "{}", resp.getBody(), resp, stepVars)).isTrue();
        Assertions.assertThat(FieldCorrelationEvaluator.evaluateBooleanExpression(
                "size($) == 2 & size(scalar) == 1 & size(missing) == 0 & plainVar == 'pv'",
                "{}", resp.getBody(), resp, stepVars)).isTrue();
        Assertions.assertThat(FieldCorrelationEvaluator.evaluateBooleanExpression(
                "size($) == 2", "{}", "[10,20]", resp, stepVars)).isTrue();
        Assertions.assertThat(FieldCorrelationEvaluator.evaluateBooleanExpression(
                "T(org.apache.commons.lang3.StringUtils).isNotBlank('x')", "{}", resp.getBody(), resp, stepVars)).isTrue();
        Assertions.assertThat(FieldCorrelationEvaluator.evaluateBooleanExpression(
                "true == true & false == false & 'a=b' == 'a=b' & \"x|y\" == \"x|y\" & 5 <= 5",
                "{}", resp.getBody(), resp, stepVars)).isTrue();
        Assertions.assertThat(FieldCorrelationEvaluator.resolveStepReference("", stepVars)).isEmpty();
        Assertions.assertThat(FieldCorrelationEvaluator.resolveStepReference("steps.s1.other", stepVars)).isEmpty();

        // Cover protectedFields array-indexed & leaf matching, non-string rawValue, null/blank json size, and request fallback
        Assertions.assertThat(FieldCorrelationEvaluator.setOrInsertField("{\"a\":\"prim\"}", "a#b#c", 123, false))
                .isEqualTo("{\"a\":\"prim\"}");
        Assertions.assertThat(FieldCorrelationEvaluator.setOrInsertField("{\"arr\":[1]}", "arr[5]#b", 123, true))
                .isEqualTo("{\"arr\":[1]}");
        Assertions.assertThat(FieldCorrelationEvaluator.evaluateBooleanExpression(
                "reqOnlyField == 'fromReq' & varOnly == 'v1' & size(missing) == 0",
                "{\"reqOnlyField\":\"fromReq\"}", "{\"other\":1}", resp, Map.of("varOnly", "v1"))).isTrue();
        Assertions.assertThat(FieldCorrelationEvaluator.evaluateBooleanExpression(
                "size($) == 0", null, "", null, Map.of())).isTrue();
        Assertions.assertThat(FieldCorrelationEvaluator.evaluateBooleanExpression(
                "size($) == 0", "{}", "123", resp, Map.of())).isTrue();
    }
}
