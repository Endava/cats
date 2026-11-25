package com.endava.cats.fuzzer.special;

import com.endava.cats.exception.CatsException;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.BasePayloadMode;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowStep;
import com.endava.cats.http.HttpMethod;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
                headers:
                  X-Global-Tenant: "${tenant}"
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
        Assertions.assertThat(doc.headers()).containsEntry("X-Global-Tenant", "${tenant}");
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
    void shouldRejectNullMissingAndInvalidInputs(@TempDir Path tempDir) throws Exception {
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowFile(null)).isFalse();
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowFile(tempDir.resolve("missing.yml").toFile())).isFalse();
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowYaml("")).isFalse();
        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowYaml(":::invalid:::yaml")).isFalse();
        Assertions.assertThat(FunctionalFlowParser.parseCorrelationFile(null)).isEmpty();
        Assertions.assertThat(FunctionalFlowParser.parseContent("").steps()).isEmpty();
        Assertions.assertThat(FunctionalFlowParser.isSecurityFlowFile(null)).isFalse();
    }

    @Test
    void shouldParseExplicitMethodPathAndSaveVariants() {
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
    }

    @Test
    void shouldParseArrayCorrelationFileThatIsNotASecurityFlow(@TempDir Path tempDir) throws Exception {
        File corrArrayFile = tempDir.resolve("corr-array.yml").toFile();
        Files.writeString(corrArrayFile.toPath(), """
                - paths: ["/items"]
                  if: "kind == 'A'"
                  set:
                    code: 1
                """);
        Assertions.assertThat(FunctionalFlowParser.parseCorrelationFile(corrArrayFile)).hasSize(1);
        Assertions.assertThat(FunctionalFlowParser.isSecurityFlowFile(corrArrayFile)).isFalse();
    }

    @Test
    void shouldParseSecurityFlowStepWithAllMethodsAndFuzzConfiguration(@TempDir Path tempDir) throws Exception {
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
        FunctionalFlowStep fuzzStep = FunctionalFlowParser.parse(secFlowFile).steps().getFirst();
        Assertions.assertThat(fuzzStep.allMethods()).isTrue();
        Assertions.assertThat(fuzzStep.scenarioText()).isEqualTo("Fuzz step with ALL methods");
        Assertions.assertThat(fuzzStep.path()).containsEntry("id", 42L);
        Assertions.assertThat(fuzzStep.expect().maxResponseTimeMs()).isEqualTo(500L);
        Assertions.assertThat(fuzzStep.fuzz().isConfigured()).isTrue();
        Assertions.assertThat(fuzzStep.fuzz().hasPayloadSource()).isTrue();
        Assertions.assertThat(fuzzStep.fuzz().hasTargets()).isTrue();
        Assertions.assertThat(fuzzStep.fuzz().mode()).isEqualTo(FunctionalFlowDocument.FuzzMode.CROSS);
    }

    @Test
    void shouldDefaultFuzzModeToSingle() {
        Assertions.assertThat(FunctionalFlowDocument.FuzzMode.fromString("single")).isEqualTo(FunctionalFlowDocument.FuzzMode.SINGLE);
        Assertions.assertThat(FunctionalFlowDocument.FuzzMode.fromString(null)).isEqualTo(FunctionalFlowDocument.FuzzMode.SINGLE);
    }

    @Test
    void shouldUseDescriptionAsNameAndIgnoreBlankSaveEntries() {
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
        Assertions.assertThat(FunctionalFlowParser.parseContent(floatAndEmptySaveYaml).steps().getFirst().save()).isEmpty();
    }

    @Test
    void shouldParseAdvancedStepOptions() {
        String advancedStepYaml = """
                steps:
                  - name: asyncAndParameterizedStep
                    GET: /items
                    delayMs: 10
                    repeat: 3
                    poll:
                      maxAttempts: 4
                      intervalMs: 5
                    forEach:
                      - tier: GOLD
                        expectedCode: 200
                      - tier: SILVER
                        expectedCode: 200
                    correlate:
                      when: "tier == GOLD"
                      set:
                        priority: 1
                """;
        FunctionalFlowStep advStep = FunctionalFlowParser.parseContent(advancedStepYaml).steps().getFirst();
        Assertions.assertThat(advStep.delayMs()).isEqualTo(10L);
        Assertions.assertThat(advStep.repeat()).isEqualTo(3);
        Assertions.assertThat(advStep.poll()).isNotNull();
        Assertions.assertThat(advStep.poll().maxAttempts()).isEqualTo(4);
        Assertions.assertThat(advStep.poll().intervalMs()).isEqualTo(5L);
        Assertions.assertThat(advStep.forEach()).hasSize(2);
        Assertions.assertThat(advStep.correlate()).hasSize(1);
    }

    @Test
    void shouldPreserveTypedGlobalVariablesAndStepTags() {
        String yaml = """
                variables:
                  expiryMonth: 12
                  rate: 19.95
                  enabled: false
                  cardNumber: "4111111111111111"
                  currencies: ["EUR", "USD"]
                  meta:
                    region: "EU"
                  nullable: null
                steps:
                  - name: auth01Authorize
                    tags: [psp, smoke]
                    POST: /authorize
                  - name: ops01MissingKey
                    tag: adapter
                    POST: /authorize
                """;

        FunctionalFlowDocument doc = FunctionalFlowParser.parseContent(yaml);
        Assertions.assertThat(doc.variables())
                .containsEntry("expiryMonth", 12L)
                .containsEntry("rate", new BigDecimal("19.95"))
                .containsEntry("enabled", false)
                .containsEntry("cardNumber", "4111111111111111")
                .containsEntry("currencies", List.of("EUR", "USD"))
                .containsEntry("meta", Map.of("region", "EU"))
                .containsEntry("nullable", null);

        FunctionalFlowStep pspStep = doc.steps().getFirst();
        FunctionalFlowStep adapterStep = doc.steps().get(1);
        Assertions.assertThat(pspStep.tags()).containsExactly("psp", "smoke");
        Assertions.assertThat(adapterStep.tags()).containsExactly("adapter");
        Assertions.assertThat(pspStep.matchesTags(List.of("PSP"), List.of())).isTrue();
        Assertions.assertThat(pspStep.matchesTags(List.of("psp"), List.of("smoke"))).isFalse();
        Assertions.assertThat(adapterStep.matchesTags(List.of("psp"), List.of())).isFalse();
        Assertions.assertThat(adapterStep.matchesTags(List.of(), List.of("psp"))).isTrue();
    }

    @Test
    void shouldResolveRelativeImportsAndMergeVariablesHeadersCorrelateAndSteps(@TempDir Path tempDir) throws Exception {
        Path commonDir = Files.createDirectories(tempDir.resolve("common/psp"));
        Path serviceDir = Files.createDirectories(tempDir.resolve("services/adyen-mock"));

        Files.writeString(commonDir.resolve("flows.yml"), """
                variables:
                  route: "default-route"
                  expiryMonth: 12
                  sharedOnly: true
                headers:
                  X-Route: "${route}"
                  X-Drop: "from-common"
                correlate:
                  - paths: ["/payments"]
                    when: "type == 'CARD'"
                    set:
                      channel: "ECOM"
                steps:
                  - name: auth01Authorize
                    tags: [psp]
                    POST: /payments
                    status: 201
                """);

        File childFile = serviceDir.resolve("flows.yml").toFile();
        Files.writeString(childFile.toPath(), """
                imports:
                  - "../../common/psp/flows.yml"
                variables:
                  route: "XeleratorMockECOM"
                  expiryMonth: 1
                headers:
                  x-route: "override-${route}"
                  X-Drop: null
                correlate:
                  - paths: ["/refunds"]
                    when: "reason == 'DUP'"
                    set:
                      autoApprove: true
                steps:
                  - name: localExtraCheck
                    tags: [adapter]
                    GET: /health
                    status: 200
                """);

        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowFile(childFile)).isTrue();
        FunctionalFlowDocument doc = FunctionalFlowParser.parse(childFile);
        Assertions.assertThat(doc.variables())
                .containsEntry("route", "XeleratorMockECOM")
                .containsEntry("expiryMonth", 1L)
                .containsEntry("sharedOnly", true);
        Assertions.assertThat(doc.headers())
                .containsEntry("x-route", "override-${route}")
                .containsEntry("X-Drop", null)
                .doesNotContainKey("X-Route");
        Assertions.assertThat(doc.correlate()).hasSize(2);
        Assertions.assertThat(doc.steps())
                .extracting(FunctionalFlowStep::name)
                .containsExactly("auth01Authorize", "localExtraCheck");
    }

    @Test
    void shouldDetectImportsOnlyFileAndDeduplicateDiamondImports(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("base.yml"), """
                variables:
                  v: 1
                steps:
                  - name: baseStep
                    POST: /base
                    status: 200
                """);
        Files.writeString(tempDir.resolve("left.yml"), """
                imports: ["base.yml"]
                variables:
                  v: 2
                steps:
                  - name: leftStep
                    POST: /left
                    status: 200
                """);
        Files.writeString(tempDir.resolve("right.yml"), """
                imports: ["base.yml"]
                variables:
                  r: "ok"
                steps:
                  - name: rightStep
                    POST: /right
                    status: 200
                """);
        File rootFile = tempDir.resolve("root.yml").toFile();
        Files.writeString(rootFile.toPath(), """
                imports: ["left.yml", "right.yml"]
                variables:
                  env: "qa"
                """);

        Assertions.assertThat(FunctionalFlowParser.isFunctionalFlowFile(rootFile)).isTrue();
        FunctionalFlowDocument doc = FunctionalFlowParser.parse(rootFile);
        Assertions.assertThat(doc.steps())
                .extracting(FunctionalFlowStep::name)
                .containsExactly("baseStep", "leftStep", "rightStep");
        Assertions.assertThat(doc.variables())
                .containsEntry("v", 2L)
                .containsEntry("r", "ok")
                .containsEntry("env", "qa");
    }

    @Test
    void shouldRejectImportCyclesMissingImportsAndStringContentImports(@TempDir Path tempDir) throws Exception {
        File fileA = tempDir.resolve("a.yml").toFile();
        File fileB = tempDir.resolve("b.yml").toFile();
        Files.writeString(fileA.toPath(), "imports: [\"b.yml\"]\n");
        Files.writeString(fileB.toPath(), "imports: [\"a.yml\"]\n");

        Assertions.assertThatThrownBy(() -> FunctionalFlowParser.parse(fileA))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("Cyclic functional flow import detected");

        File missingRef = tempDir.resolve("missing-ref.yml").toFile();
        Files.writeString(missingRef.toPath(), "imports: [\"does-not-exist.yml\"]\n");
        Assertions.assertThatThrownBy(() -> FunctionalFlowParser.parse(missingRef))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("Imported functional flow file not found");

        Assertions.assertThatThrownBy(() -> FunctionalFlowParser.parseContent("imports: [\"other.yml\"]\n"))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("requires loading from a file");

        Path bracketDir = Files.createDirectories(tempDir.resolve("dir[1]"));
        Files.writeString(bracketDir.resolve("shared.yml"), "steps:\n  - POST: /ok\n");
        File singleStringImport = tempDir.resolve("single-import.yml").toFile();
        Files.writeString(singleStringImport.toPath(), "imports: \"dir[1]/shared.yml\"\n");
        Assertions.assertThat(FunctionalFlowParser.parse(singleStringImport).steps()).hasSize(1);

        Files.writeString(tempDir.resolve("broken.yml"), "steps:\n  - [unclosed\n");
        File importsBroken = tempDir.resolve("imports-broken.yml").toFile();
        Files.writeString(importsBroken.toPath(), "imports: [\"broken.yml\"]\n");
        Assertions.assertThatThrownBy(() -> FunctionalFlowParser.parse(importsBroken))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("broken.yml");
    }

    @Test
    void shouldParseFlowLevelStepTagsHeadersRefAndOverrideRules(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("common.yml"), """
                stepTags: [psp]
                skipStepTags: [slow]
                headers:
                  X-From-Common: "yes"
                steps:
                  - name: sendWebhook
                    tags: [psp]
                    POST: /webhooks
                    headers: "${webhookHeaders}"
                    body: "${webhookBody}"
                    omit: "${omitList}"
                """);
        File childFile = tempDir.resolve("child.yml").toFile();
        Files.writeString(childFile.toPath(), """
                imports: ["common.yml"]
                stepTags: [adapter]
                skipStepTags: []
                headers: "${docHeaders}"
                variables:
                  docHeaders:
                    X-Doc: "1"
                  webhookHeaders:
                    X-Sig: "abc"
                  webhookBody:
                    id: "evt_1"
                  omitList: ["route"]
                """);

        FunctionalFlowDocument doc = FunctionalFlowParser.parse(childFile);
        Assertions.assertThat(doc.stepTags()).containsExactly("adapter");
        Assertions.assertThat(doc.skipStepTags()).isEmpty();
        Assertions.assertThat(doc.headers()).isEmpty();
        Assertions.assertThat(doc.headersRef()).isEqualTo("${docHeaders}");
        Assertions.assertThat(doc.steps().getFirst().headersRef()).isEqualTo("${webhookHeaders}");
        Assertions.assertThat(doc.steps().getFirst().omit()).containsExactly("${omitList}");

        Assertions.assertThatThrownBy(() -> FunctionalFlowParser.parseContent("steps:\n  - POST: /x\n    headers: [1, 2]\n"))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("Invalid 'headers' value");
    }
}
