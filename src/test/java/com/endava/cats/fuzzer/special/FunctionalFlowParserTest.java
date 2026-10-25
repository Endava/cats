package com.endava.cats.fuzzer.special;

import com.endava.cats.fuzzer.special.FunctionalFlowDocument.BasePayloadMode;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowStep;
import com.endava.cats.http.HttpMethod;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@QuarkusTest
class FunctionalFlowParserTest {

    @Test
    void shouldDetectFunctionalFlowYamlAndRejectLegacyOrSecurityYaml(@TempDir Path tempDir) throws Exception {
        String flowYaml = """
                variables:
                  baseName: "Plan"
                correlate:
                  - paths: ["/billing-plans*"]
                    when: "counter.metric == 'COUNT'"
                    then:
                      omit: ["counter.metricField"]
                steps:
                  - name: Create plan
                    POST: /billing-plans
                    body:
                      name: "${baseName}-1"
                    status: 201
                """;
        File flowFile = tempDir.resolve("flow.yml").toFile();
        Files.writeString(flowFile.toPath(), flowYaml);

        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowFile(flowFile)).isTrue();
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowFile(new File("src/test/resources/functionalFuzzer.yml"))).isFalse();
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowFile(new File("src/test/resources/securityFuzzer.yml"))).isFalse();
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowFile(null)).isFalse();
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowYaml("")).isFalse();
    }

    @Test
    void shouldDetectTopLevelArrayStepFormat() {
        String arrayYaml = """
                - name: Create item
                  POST: /items
                  status: 201
                - method: GET
                  path: /items/1
                """;
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowYaml(arrayYaml)).isTrue();
        FunctionalFlowDocument doc = FunctionalFlowParser.parseContent(arrayYaml);
        Assertions.assertThat(doc.steps()).hasSize(2);
        Assertions.assertThat(doc.steps().getFirst().method()).isEqualTo(HttpMethod.POST);
        Assertions.assertThat(doc.steps().get(1).method()).isEqualTo(HttpMethod.GET);
    }

    @Test
    void shouldParseCompleteDocumentWithVariablesCorrelationsAndStepOptions() {
        String yaml = """
                variables:
                  tenant: "acme"
                correlate:
                  - paths: ["/rules*"]
                    when: "metric == 'SUM'"
                    then:
                      set:
                        metricField: "amount"
                      omit: ["unused"]
                steps:
                  - name: Create velocity rule
                    POST: /rules
                    base: onlyRequired
                    variant: VelocityRule
                    omit: [id, createdAt]
                    headers:
                      X-Tenant: "${tenant}"
                    query:
                      dryRun: false
                    body:
                      ruleType: "VELOCITY"
                      priority: 10
                      metadata:
                        env: "staging"
                    status: [200, 201]
                    save:
                      createdId: ruleId
                    expect:
                      active: true
                      headers:
                        content-type: "application/json"
                      bodyContains: ["VELOCITY"]
                      bodyNotContains: ["ERROR"]
                      where:
                        - "priority >= 10 & active = true"
                      matchesSchema: false
                """;

        FunctionalFlowDocument doc = FunctionalFlowParser.parseContent(yaml);
        Assertions.assertThat(doc.variables()).containsEntry("tenant", "acme");
        Assertions.assertThat(doc.correlate()).singleElement().satisfies(rule -> {
            Assertions.assertThat(rule.paths()).containsExactly("/rules*");
            Assertions.assertThat(rule.when()).isEqualTo("metric == 'SUM'");
            Assertions.assertThat(rule.set()).containsEntry("metricField", "amount");
            Assertions.assertThat(rule.omit()).containsExactly("unused");
        });

        Assertions.assertThat(doc.steps()).hasSize(1);
        FunctionalFlowStep step = doc.steps().getFirst();
        Assertions.assertThat(step.name()).isEqualTo("Create velocity rule");
        Assertions.assertThat(step.method()).isEqualTo(HttpMethod.POST);
        Assertions.assertThat(step.rawTarget()).isEqualTo("/rules");
        Assertions.assertThat(step.base()).isEqualTo(BasePayloadMode.ONLY_REQUIRED);
        Assertions.assertThat(step.variant()).isEqualTo("VelocityRule");
        Assertions.assertThat(step.omit()).containsExactly("id", "createdAt");
        Assertions.assertThat(step.headers()).containsEntry("X-Tenant", "${tenant}");
        Assertions.assertThat(step.query()).containsEntry("dryRun", false);
        Assertions.assertThat(step.save()).containsEntry("createdId", "ruleId");
        Assertions.assertThat(step.expect()).satisfies(expect -> {
            Assertions.assertThat(expect.status()).containsExactly("200", "201");
            Assertions.assertThat(expect.fields()).containsEntry("active", true);
            Assertions.assertThat(expect.headers()).containsEntry("content-type", "application/json");
            Assertions.assertThat(expect.bodyContains()).containsExactly("VELOCITY");
            Assertions.assertThat(expect.bodyNotContains()).containsExactly("ERROR");
            Assertions.assertThat(expect.where()).containsExactly("priority >= 10 & active = true");
            Assertions.assertThat(expect.matchesSchema()).isFalse();
        });
        Assertions.assertThat(step.body()).isInstanceOf(Map.class);
    }

    @Test
    void shouldParseMapVariantAndDefaultBaseModes() {
        String yaml = """
                steps:
                  - POST: /rules
                    base: empty
                    variant:
                      ruleType: STATIC
                """;
        FunctionalFlowDocument doc = FunctionalFlowParser.parseContent(yaml);
        FunctionalFlowStep step = doc.steps().getFirst();
        Assertions.assertThat(step.base()).isEqualTo(BasePayloadMode.EMPTY);
        Assertions.assertThat(step.variantMatch()).containsEntry("ruleType", "STATIC");
        Assertions.assertThat(step.name()).isEqualTo("Step 1: POST /rules");
        Assertions.assertThat(step.expect().status()).containsExactly("2XX");
    }

    @Test
    void shouldFailWhenStepMissingMethodAndPath() {
        String invalidYaml = """
                steps:
                  - name: Missing method
                    status: 200
                """;
        Assertions.assertThatThrownBy(() -> FunctionalFlowParser.parseContent(invalidYaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must specify an HTTP method");
    }
}
