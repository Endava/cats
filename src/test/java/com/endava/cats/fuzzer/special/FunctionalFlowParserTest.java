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
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowFile(new File("src/test/resources/securityFuzzer.yml"))).isTrue();
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowYaml("/pet:\n  test1:\n    httpMethod: POST\n")).isFalse();
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
                      that:
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
        Assertions.assertThat(step)
                .extracting(FunctionalFlowStep::name, FunctionalFlowStep::method, FunctionalFlowStep::rawTarget,
                        FunctionalFlowStep::base, FunctionalFlowStep::variant)
                .containsExactly("Create velocity rule", HttpMethod.POST, "/rules",
                        BasePayloadMode.ONLY_REQUIRED, "VelocityRule");
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
            Assertions.assertThat(expect.that()).containsExactly("priority >= 10 & active = true");
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

    @Test
    void shouldHandleExplicitMethodPathAndEdgeCases(@TempDir Path tempDir) throws Exception {
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowFile(null)).isFalse();
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowFile(tempDir.resolve("missing.yml").toFile())).isFalse();
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowYaml("")).isFalse();
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowYaml(":::invalid:::yaml")).isFalse();
        Assertions.assertThat(FunctionalFlowParser.parseCorrelationFile(null)).isEmpty();

        String explicitMethodPathYaml = """
                - method: POST
                  path: /items
                  save:
                    - id
                    - nested.code
                    - aliased: otherField
                  expect:
                    status: 201
                - method: GET
                  path: /items
                  save: "singleField, secondField"
                """;
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowYaml(explicitMethodPathYaml)).isTrue();
        FunctionalFlowDocument doc = FunctionalFlowParser.parseContent(explicitMethodPathYaml);
        Assertions.assertThat(doc.steps()).hasSize(2);
        Assertions.assertThat(doc.steps().getFirst()).satisfies(step -> {
            Assertions.assertThat(step.method()).isEqualTo(HttpMethod.POST);
            Assertions.assertThat(step.rawTarget()).isEqualTo("/items");
            Assertions.assertThat(step.save())
                    .containsEntry("id", "id")
                    .containsEntry("code", "nested.code")
                    .containsEntry("aliased", "otherField");
            Assertions.assertThat(step.expect().status()).containsExactly("201");
        });
        Assertions.assertThat(doc.steps().get(1).save())
                .containsEntry("singleField", "singleField")
                .containsEntry("secondField", "secondField");

        File corrArrayFile = tempDir.resolve("corr-array.yml").toFile();
        Files.writeString(corrArrayFile.toPath(), """
                - paths: ["/items"]
                  if: "kind == 'A'"
                  set:
                    code: 1
                """);
        Assertions.assertThat(FunctionalFlowParser.parseCorrelationFile(corrArrayFile)).hasSize(1);

        File secFlowFile = tempDir.resolve("sec-flow.yml").toFile();
        Files.writeString(secFlowFile.toPath(), """
                steps:
                  - description: Fuzz step with ALL methods
                    ALL: /items
                    path:
                      id: 42
                    body:
                      price: 19.99
                    fuzz:
                      fields: [price]
                      payloads: ["' OR 1=1--"]
                      mode: cross
                    expect:
                      maxResponseTimeMs: 500
                """);
        Assertions.assertThat(FunctionalFlowParser.isSecurityFlowFile(secFlowFile)).isTrue();
        Assertions.assertThat(FunctionalFlowParser.isSecurityFlowFile(corrArrayFile)).isFalse();
        FunctionalFlowDocument secDoc = FunctionalFlowParser.parse(secFlowFile);
        FunctionalFlowStep fuzzStep = secDoc.steps().getFirst();
        Assertions.assertThat(fuzzStep.allMethods()).isTrue();
        Assertions.assertThat(fuzzStep.scenarioText()).isEqualTo("Fuzz step with ALL methods");
        Assertions.assertThat(fuzzStep.path()).containsEntry("id", 42L);
        Assertions.assertThat(fuzzStep.expect().maxResponseTimeMs()).isEqualTo(500L);
        Assertions.assertThat(fuzzStep.fuzz().isConfigured()).isTrue();
        Assertions.assertThat(fuzzStep.fuzz().hasPayloadSource()).isTrue();
        Assertions.assertThat(fuzzStep.fuzz().hasTargets()).isTrue();
        Assertions.assertThat(fuzzStep.fuzz().mode()).isEqualTo(FunctionalFlowDocument.FuzzMode.CROSS);
        Assertions.assertThat(FunctionalFlowDocument.FuzzMode.fromString("single")).isEqualTo(FunctionalFlowDocument.FuzzMode.SINGLE);
        Assertions.assertThat(FunctionalFlowDocument.FuzzMode.fromString(null)).isEqualTo(FunctionalFlowDocument.FuzzMode.SINGLE);

        String pathsAndDescStep = """
                steps:
                  - description: Only description provided
                    paths: ["/a", "/b"]
                    fuzz:
                      fieldTypes: [string]
                    variant: 123
                    save: 123
                """;
        FunctionalFlowDocument pathsDoc = FunctionalFlowParser.parseContent(pathsAndDescStep);
        Assertions.assertThat(pathsDoc.steps().getFirst().name()).isEqualTo("Only description provided");
        Assertions.assertThat(pathsDoc.steps().getFirst().targetPaths()).containsExactly("/a", "/b");

        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowFile(null)).isFalse();
        Assertions.assertThat(FunctionalFlowParser.parseCorrelationFile(null)).isEmpty();
        Assertions.assertThat(FunctionalFlowParser.parseContent("").steps()).isEmpty();
        String floatAndEmptySaveYaml = """
                steps:
                  - POST: /items
                    body:
                      price: 19.99
                      nilVal: null
                    save:
                      - "   "
                    expect:
                      status: ""
                """;
        FunctionalFlowDocument floatDoc = FunctionalFlowParser.parseContent(floatAndEmptySaveYaml);
        Assertions.assertThat(floatDoc.steps().getFirst().save()).isEmpty();
        Assertions.assertThat(FunctionalFlowParser.isSecurityFlowFile(null)).isFalse();
    }
}
