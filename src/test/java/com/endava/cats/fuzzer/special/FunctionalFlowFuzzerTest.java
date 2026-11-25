package com.endava.cats.fuzzer.special;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.util.KeyValuePair;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.ObjectSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class FunctionalFlowFuzzerTest {
    private FilesArguments filesArguments;
    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private FunctionalFlowFuzzer fuzzer;

    @BeforeEach
    void setup() {
        filesArguments = new FilesArguments();
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        fuzzer = new FunctionalFlowFuzzer(filesArguments, serviceCaller, testCaseListener);
    }

    @Test
    void shouldExecuteSequentialFlowWithInlineParamsCorrelationsAndRefDataReplacement(@TempDir Path tempDir) throws Exception {
        String yaml = """
                variables:
                  prefix: "Plan"
                correlate:
                  - paths: ["/billing-plans*"]
                    when: "planType == 'STATIC'"
                    then:
                      omit: ["metricField"]
                steps:
                  - name: Create static plan
                    POST: /billing-plans
                    headers:
                      X-Correlation-Id: "#(uuid)"
                    omit: [id]
                    body:
                      planType: "STATIC"
                      name: "${prefix}-Alpha"
                      metadata:
                        owner: "billing-team"
                    status: 201
                    save:
                      createdPlanId: planId
                    expect:
                      planType: "STATIC"
                      headers:
                        X-Server: "billing"
                      bodyContains: ["plan_42"]
                      that:
                        - "priority >= 10 & $request.planType = 'STATIC'"

                  - name: Fetch and patch created plan
                    PATCH: /billing-plans/${createdPlanId}?dryRun=false
                    body:
                      active: false
                    status: 200
                    expect:
                      planId: "${createdPlanId}"
                      active: false
                """;
        File flowFile = tempDir.resolve("flow.yml").toFile();
        Files.writeString(flowFile.toPath(), yaml);
        File exportRefData = tempDir.resolve("exported-refdata.yml").toFile();

        filesArguments.setFunctionalFlowFile(flowFile);
        filesArguments.setExportRefDataFile(exportRefData);

        FuzzingData postPlans = FuzzingData.builder()
                .method(HttpMethod.POST)
                .path("/billing-plans")
                .contractPath("/billing-plans")
                .payload("{\"id\":\"remove-me\",\"planType\":\"STATIC\",\"name\":\"Default\",\"metricField\":\"drop\",\"metadata\":{}}")
                .allRequiredFields(List.of("planType", "name"))
                .reqSchema(new ObjectSchema())
                .schemaMap(Map.of())
                .headers(Set.of(CatsHeader.builder().name("X-Correlation-Id").value("old").build()))
                .responseCodes(Set.of("201"))
                .requestContentTypes(List.of("application/json"))
                .build();

        FuzzingData patchPlan = FuzzingData.builder()
                .method(HttpMethod.PATCH)
                .path("/billing-plans/{planId}")
                .contractPath("/billing-plans/{planId}")
                .payload("{\"active\":true}")
                .pathParamsPayload("{\"planId\":\"RANDOM\"}")
                .queryParams(Set.of("dryRun"))
                .allRequiredFields(List.of())
                .reqSchema(new ObjectSchema())
                .schemaMap(Map.of())
                .headers(Set.of())
                .responseCodes(Set.of("200"))
                .requestContentTypes(List.of("application/json"))
                .build();

        fuzzer.fuzz(postPlans);
        fuzzer.fuzz(patchPlan);

        List<ServiceData> capturedCalls = new ArrayList<>();
        Mockito.when(serviceCaller.call(Mockito.any())).thenAnswer(inv -> {
            ServiceData sd = inv.getArgument(0);
            capturedCalls.add(sd);
            if (sd.getHttpMethod() == HttpMethod.POST) {
                String body = "{\"planId\":\"plan_42\",\"planType\":\"STATIC\",\"priority\":100}";
                return CatsResponse.builder()
                        .responseCode(201)
                        .body(body)
                        .jsonBody(JsonParser.parseString(body))
                        .headers(List.of(new KeyValuePair<>("X-Server", "billing")))
                        .build();
            }
            String body = "{\"planId\":\"plan_42\",\"active\":false}";
            return CatsResponse.builder()
                    .responseCode(200)
                    .body(body)
                    .jsonBody(JsonParser.parseString(body))
                    .headers(List.of())
                    .build();
        });

        fuzzer.executeFlows();

        Assertions.assertThat(capturedCalls).hasSize(2);
        ServiceData postCall = capturedCalls.getFirst();
        Assertions.assertThat(postCall.getPayload())
                .contains("\"name\":\"Plan-Alpha\"")
                .contains("\"owner\":\"billing-team\"")
                .doesNotContain("\"id\"")
                .doesNotContain("metricField");
        Assertions.assertThat(postCall.getHeaders())
                .anySatisfy(h -> Assertions.assertThat(h.getValue()).matches("[0-9a-f\\-]{36}"));

        ServiceData patchCall = capturedCalls.get(1);
        Assertions.assertThat(patchCall.getPathParamsPayload())
                .contains("\"planId\":\"plan_42\"")
                .contains("\"dryRun\":\"false\"");
        Assertions.assertThat(patchCall.getPayload()).contains("\"active\":false");

        Assertions.assertThat(exportRefData).exists();
        Assertions.assertThat(Files.readString(exportRefData.toPath())).contains("plan_42");
        Assertions.assertThat(fuzzer).hasToString("FunctionalFlowFuzzer");
        Assertions.assertThat(fuzzer.description()).isNotBlank();
    }

    @Test
    void shouldSelectVariantAndReportAssertionFailures(@TempDir Path tempDir) throws Exception {
        String yaml = """
                steps:
                  - name: Create metered plan
                    POST: /billing-plans
                    variant: MeteredPlan
                    base: empty
                    body:
                      planType: "METERED"
                    status: 201
                    expect:
                      planType: "STATIC"
                      headers:
                        X-Missing: "val"
                      bodyContains: ["must_exist"]
                      bodyNotContains: ["METERED"]
                      that:
                        - "score > 50"
                """;
        File flowFile = tempDir.resolve("flow-fail.yml").toFile();
        Files.writeString(flowFile.toPath(), yaml);
        filesArguments.setFunctionalFlowFile(flowFile);

        FuzzingData staticVariant = FuzzingData.builder()
                .method(HttpMethod.POST).path("/billing-plans").contractPath("/billing-plans")
                .reqSchemaName("StaticPlan").payload("{\"planType\":\"STATIC\"}")
                .reqSchema(new ObjectSchema()).schemaMap(Map.of()).headers(Set.of())
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();
        FuzzingData meteredVariant = FuzzingData.builder()
                .method(HttpMethod.POST).path("/billing-plans").contractPath("/billing-plans")
                .reqSchemaName("MeteredPlan").payload("{\"planType\":\"METERED\"}")
                .reqSchema(new ObjectSchema()).schemaMap(Map.of()).headers(Set.of())
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();

        fuzzer.fuzz(staticVariant);
        fuzzer.fuzz(meteredVariant);

        String respBody = "{\"planType\":\"METERED\",\"score\":10}";
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder().responseCode(201).body(respBody).jsonBody(JsonParser.parseString(respBody)).headers(List.of()).build());

        fuzzer.executeFlows();

        Mockito.verify(testCaseListener).reportResultError(
                Mockito.any(), Mockito.eq(meteredVariant),
                Mockito.eq("Verify parameters not matching response"),
                Mockito.contains("score > 50"), Mockito.any());
    }

    @Test
    void shouldPreferExplicitVariantFieldsOverBodyFieldsWhenSelectingVariant(@TempDir Path tempDir) throws Exception {
        String yaml = """
                steps:
                  - name: Create email subscription
                    POST: /subscriptions
                    variant:
                      channel.type: "EMAIL"
                    body:
                      deliveryMode: "INSTANT"
                    status: 201
                """;
        File flowFile = tempDir.resolve("flow-variant.yml").toFile();
        Files.writeString(flowFile.toPath(), yaml);
        filesArguments.setFunctionalFlowFile(flowFile);

        FuzzingData smsVariant = FuzzingData.builder()
                .method(HttpMethod.POST).path("/subscriptions").contractPath("/subscriptions")
                .reqSchemaName("SubscriptionIn").payload("{\"deliveryMode\":\"INSTANT\",\"channel\":{\"type\":\"SMS\"}}")
                .reqSchema(new ObjectSchema()).schemaMap(Map.of()).headers(Set.of())
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();
        FuzzingData emailVariant = FuzzingData.builder()
                .method(HttpMethod.POST).path("/subscriptions").contractPath("/subscriptions")
                .reqSchemaName("SubscriptionIn").payload("{\"deliveryMode\":\"DIGEST\",\"channel\":{\"type\":\"EMAIL\"}}")
                .reqSchema(new ObjectSchema()).schemaMap(Map.of()).headers(Set.of())
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();

        fuzzer.fuzz(smsVariant);
        fuzzer.fuzz(emailVariant);

        ArgumentCaptor<ServiceData> callCaptor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.when(serviceCaller.call(callCaptor.capture())).thenReturn(
                CatsResponse.builder().responseCode(201).body("{}").jsonBody(JsonParser.parseString("{}")).headers(List.of()).build());

        fuzzer.executeFlows();

        Assertions.assertThat(callCaptor.getValue().getPayload())
                .contains("\"type\":\"EMAIL\"")
                .contains("\"deliveryMode\":\"INSTANT\"");
    }

    @Test
    void shouldHandleStringAndListBodiesNestedMapsInlineQueryParamsAndSelectors(@TempDir Path tempDir) throws Exception {
        String flowYaml = """
                steps:
                  - name: Create with nested map and shorthand scalar coercion
                    POST: /billing-plans?region=EU&flag
                    base: empty
                    headers:
                      X-Trace: "#(uuid)"
                    body:
                      name: "Plan-A"
                      count: "#(randomInt(1,5))"
                      enabled: "T(java.lang.Boolean).TRUE"
                      tags: ["tag1", "tag2"]
                      nested:
                        tier: "GOLD"
                    save:
                      savedId: "id"
                      reqName: "$request.name"
                      hdrVal: "$headers.X-Rate"
                    expect:
                      status: 201
                      headers:
                        X-Rate: "100"
                      bodyContains: ["Plan-A"]
                      bodyNotContains: ["FORBIDDEN"]
                  - name: Send raw string body
                    POST: /billing-plans
                    body: "{\\"raw\\":\\"${savedId}\\"}"
                    expect:
                      status: 201
                  - name: Send list body
                    POST: /billing-plans
                    body:
                      - "${savedId}"
                    expect:
                      status: 201
                """;
        File flowFile = tempDir.resolve("rich-flow.yml").toFile();
        Files.writeString(flowFile.toPath(), flowYaml);
        filesArguments.setFunctionalFlowFile(flowFile);
        filesArguments.setExportRefDataFile(tempDir.resolve("rich-exported.yml").toFile());
        File outRefFile = tempDir.resolve("out-ref.yml").toFile();
        Files.writeString(outRefFile.toPath(), "all:\n  x: 1\n");
        filesArguments.setRefDataFile(outRefFile);

        FuzzingData postPlan = FuzzingData.builder()
                .method(HttpMethod.POST).path("/billing-plans").contractPath("/billing-plans")
                .payload("{\"nested\":{\"existing\":\"v\"}}").reqSchema(new ObjectSchema()).schemaMap(Map.of())
                .headers(Set.of(CatsHeader.builder().name("X-Default").value("1").build()))
                .queryParams(Set.of("region", "flag"))
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();
        fuzzer.fuzz(postPlan);

        String respBody = "{\"id\":\"p_1\",\"name\":\"Plan-A\"}";
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder().responseCode(201).body(respBody)
                        .jsonBody(JsonParser.parseString(respBody))
                        .headers(List.of(new KeyValuePair<>("X-Rate", "100"))).build());

        fuzzer.executeFlows();
        Mockito.verify(serviceCaller, Mockito.times(3)).call(Mockito.any());
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("FunctionalFlowFuzzer");
    }

    @Test
    void shouldSupportStepDescriptionsAllCapabilitiesCrossStepReferencesAndFuzzBlocks(@TempDir Path tempDir) throws Exception {
        String flowYaml = """
                variables:
                  globalTag: "Enterprise"
                correlate:
                  - paths: ["/billing-plans"]
                    when: "planType == 'STATIC'"
                    then:
                      set:
                        extraMeta.region: "OVERWRITTEN"
                        extraMeta.tier: "GOLD"
                      omit:
                        - "extraMeta.unusedChild"
                steps:
                  - name: createPlan
                    description: Create enterprise plan with extra properties and field omissions
                    POST: /billing-plans
                    variant:
                      planType: "STATIC"
                    omit: [obsoleteField, obsoleteList]
                    body:
                      planType: "STATIC"
                      name: "${globalTag}-Plan"
                      extraMeta:
                        region: "EMEA"
                    save:
                      createdCode: "code"
                      reqPlanName: "request.name"
                    expect:
                      status: 201
                      name.first: "Cats"
                      planName: "$request.name"
                      maxResponseTimeMs: 500
                      that:
                        - "size($) == 5"
                        - "request.extraMeta.region == 'EMEA'"
                        - "request.extraMeta.tier == 'GOLD'"
                        - "T(java.time.LocalDate).now().isAfter(T(java.time.LocalDate).parse(expiry.toString()))"
                        - "!T(java.time.LocalDate).now().isBefore(T(java.time.LocalDate).parse(expiry.toString()))"
                  - name: getCreatedPlan
                    description: Verify cross-step $steps and $prev references
                    GET: /billing-plans/${steps.createPlan.response.id}
                    expect:
                      status: 200
                      id: "$prev.response.id"
                      that:
                        - "$steps.createPlan.status == 201"
                        - "response.id == $steps.createPlan.response.id"
                        - "$steps.createPlan.request.planType == 'STATIC'"
                  - name: crossFieldSecurityStep
                    description: Cross-field security fuzzing
                    POST: /billing-plans
                    fuzz:
                      fields: [name, planType]
                      payloads: ["<script>1</script>"]
                      mode: cross
                    expect:
                      status: 201
                """;
        File flowFile = tempDir.resolve("complete-flow.yml").toFile();
        Files.writeString(flowFile.toPath(), flowYaml);
        filesArguments.setFunctionalFlowFile(flowFile);

        FuzzingData postPlan = FuzzingData.builder()
                .method(HttpMethod.POST).path("/billing-plans").contractPath("/billing-plans")
                .payload("{\"planType\":\"STATIC\",\"name\":\"Old\",\"obsoleteField\":\"x\",\"obsoleteList\":[\"y\"]}")
                .reqSchema(new ObjectSchema()).schemaMap(Map.of()).headers(Set.of())
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();
        FuzzingData getPlan = FuzzingData.builder()
                .method(HttpMethod.GET).path("/billing-plans/{planId}").contractPath("/billing-plans/{planId}")
                .payload("{}").pathParamsPayload("{\"planId\":\"gen\"}")
                .reqSchema(new ObjectSchema()).schemaMap(Map.of()).headers(Set.of())
                .responseCodes(Set.of("200")).requestContentTypes(List.of("application/json")).build();
        fuzzer.fuzz(postPlan);
        fuzzer.fuzz(getPlan);

        Mockito.when(serviceCaller.call(Mockito.any())).thenAnswer(inv -> {
            ServiceData sd = inv.getArgument(0);
            if (sd.getHttpMethod() == HttpMethod.POST) {
                String body = "{\"id\":\"plan_99\",\"code\":\"200\",\"name\":{\"first\":\"Cats\"},\"planName\":\"Enterprise-Plan\",\"expiry\":\"2000-02-02\"}";
                return CatsResponse.builder().responseCode(201).responseTimeInMs(15).body(body)
                        .jsonBody(JsonParser.parseString(body)).headers(List.of()).build();
            }
            String body = "{\"id\":\"plan_99\",\"status\":\"ACTIVE\"}";
            return CatsResponse.builder().responseCode(200).responseTimeInMs(10).body(body)
                    .jsonBody(JsonParser.parseString(body)).headers(List.of()).build();
        });

        fuzzer.executeFlows();

        Mockito.verify(testCaseListener).addScenario(
                Mockito.any(), Mockito.eq("Scenario: {}"),
                AdditionalMatchers.aryEq(new Object[]{"Create enterprise plan with extra properties and field omissions"}));
        Mockito.verify(testCaseListener).addScenario(
                Mockito.any(), Mockito.eq("Scenario: {}"),
                AdditionalMatchers.aryEq(new Object[]{"Verify cross-step $steps and $prev references"}));
        Mockito.verify(testCaseListener).addScenario(
                Mockito.any(), Mockito.eq("Scenario: {}"),
                AdditionalMatchers.aryEq(new Object[]{"Cross-field security fuzzing, cross-fields [name, planType]"}));
        Assertions.assertThat(fuzzer.getVariables())
                .containsEntry("createdCode", "200")
                .containsEntry("reqPlanName", "Enterprise-Plan");
        ArgumentCaptor<ServiceData> captor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller, Mockito.times(3)).call(captor.capture());
        List<ServiceData> calls = captor.getAllValues();
        Assertions.assertThat(calls.get(0).getAllMutationTargets()).isNotEmpty();
        Assertions.assertThat(calls.get(0).isReplaceRefData()).isFalse();
        Assertions.assertThat(calls.get(0).isReplaceUrlParams()).isTrue();
        Assertions.assertThat(calls.get(1).getAllMutationTargets()).isNotEmpty();
        Assertions.assertThat(calls.get(2).getAllMutationTargets()).isNotEmpty();
    }

    @Test
    void shouldReportMissingVerifyFieldsAndWarnOnStatusMismatch(@TempDir Path tempDir) throws Exception {
        String flowYaml = """
                steps:
                  - description: Check missing field in response
                    POST: /billing-plans
                    expect:
                      missingAddress: "Main St"
                  - description: Check status mismatch warning when expect passes
                    POST: /billing-plans
                    status: 200
                    expect:
                      id: "plan_99"
                  - description: Check status mismatch error across families when expect passes
                    POST: /billing-plans
                    status: 400
                    expect:
                      id: "plan_99"
                """;
        File flowFile = tempDir.resolve("missing-and-warn.yml").toFile();
        Files.writeString(flowFile.toPath(), flowYaml);
        filesArguments.setFunctionalFlowFile(flowFile);

        FuzzingData postPlan = FuzzingData.builder()
                .method(HttpMethod.POST).path("/billing-plans").contractPath("/billing-plans")
                .payload("{\"name\":\"Plan\"}").reqSchema(new ObjectSchema()).schemaMap(Map.of()).headers(Set.of())
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();
        fuzzer.fuzz(postPlan);

        String respBody = "[{\"id\":\"plan_99\"}]";
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder().responseCode(201).body(respBody)
                        .jsonBody(JsonParser.parseString(respBody)).headers(List.of()).build());

        fuzzer.executeFlows();

        Mockito.verify(testCaseListener).reportResultError(
                Mockito.any(), Mockito.eq(postPlan),
                Mockito.eq("Verify parameters not present in response"),
                Mockito.eq("The following Verify parameters were not present in the response: {}"),
                Mockito.any());
        Mockito.verify(testCaseListener).reportResultWarn(
                Mockito.any(), Mockito.eq(postPlan),
                Mockito.eq("Returned response code not matching expected response code"),
                Mockito.anyString(), Mockito.any());
        Mockito.verify(testCaseListener).reportResultError(
                Mockito.any(), Mockito.eq(postPlan),
                Mockito.eq("Unexpected response code: 201"),
                Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldCoverSingleFieldFuzzingVariantMatchingOnlyRequiredPathSelectorAndCreateRefData(@TempDir Path tempDir) throws Exception {
        String flowYaml = """
                steps:
                  - name: matchVariantByMap
                    POST: /billing-plans
                    base: onlyRequired
                    variant:
                      planType: "METERED"
                    body:
                      planType: "METERED"
                    save:
                      savedPlanId: "id"
                    expect:
                      status: 201
                      maxResponseTimeMs: 1
                  - name: patchWithPathSave
                    PATCH: /billing-plans/${savedPlanId}
                    save:
                      fromPathId: "path.planId"
                    expect:
                      status: 200
                  - name: singleFieldAndHeaderFuzz
                    POST: /billing-plans
                    fuzz:
                      fields: [planType]
                      fieldTypes: [headers, body]
                      payloads: ["bad"]
                      mode: single
                    expect:
                      status: 201
                """;
        File flowFile = tempDir.resolve("extra-flow.yml").toFile();
        File exportRefData = tempDir.resolve("extra-refdata.yml").toFile();
        Files.writeString(flowFile.toPath(), flowYaml);
        filesArguments.setFunctionalFlowFile(flowFile);
        filesArguments.setExportRefDataFile(exportRefData);
        filesArguments.setRefDataFile(null);

        FuzzingData staticVariant = FuzzingData.builder()
                .method(HttpMethod.POST).path("/billing-plans").contractPath("/billing-plans")
                .reqSchemaName("StaticPlan").payload("{\"planType\":\"STATIC\",\"extra\":\"drop\"}")
                .allRequiredFields(List.of("planType"))
                .reqSchema(new ObjectSchema()).schemaMap(Map.of())
                .headers(Set.of(CatsHeader.builder().name("X-Hdr").value("v").build()))
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();
        FuzzingData meteredVariant = FuzzingData.builder()
                .method(HttpMethod.POST).path("/billing-plans").contractPath("/billing-plans")
                .reqSchemaName("MeteredPlan").payload("{\"planType\":\"METERED\",\"extra\":\"drop\"}")
                .allRequiredFields(List.of("planType"))
                .reqSchema(new ObjectSchema()).schemaMap(Map.of())
                .headers(Set.of(CatsHeader.builder().name("X-Hdr").value("v").build()))
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();
        FuzzingData patchPlan = FuzzingData.builder()
                .method(HttpMethod.PATCH).path("/billing-plans/{planId}").contractPath("/billing-plans/{planId}")
                .payload("{}").pathParamsPayload("{\"planId\":\"gen\"}")
                .reqSchema(new ObjectSchema()).schemaMap(Map.of()).headers(Set.of())
                .responseCodes(Set.of("200")).requestContentTypes(List.of("application/json")).build();

        fuzzer.fuzz(staticVariant);
        fuzzer.fuzz(meteredVariant);
        fuzzer.fuzz(patchPlan);

        String respBody = "{\"id\":\"p_77\",\"planType\":\"METERED\"}";
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder().responseCode(201).responseTimeInMs(25).body(respBody)
                        .jsonBody(JsonParser.parseString(respBody)).headers(List.of()).build());

        fuzzer.executeFlows();

        Assertions.assertThat(fuzzer.getVariables()).containsEntry("fromPathId", "p_77");
        Assertions.assertThat(exportRefData).exists();
    }

    @Test
    void shouldLoadOnlyPayloadLinesAndRejectMissingPayloadFiles(@TempDir Path tempDir) throws Exception {
        Path payloads = tempDir.resolve("payloads.txt");
        Files.writeString(payloads, "# comment line\n\npayload_one\n");
        var config = new FunctionalFlowDocument.StepFuzzConfig(List.of("name"), List.of(), List.of(),
                List.of(payloads.toString()), FunctionalFlowDocument.FuzzMode.SINGLE);
        Assertions.assertThat(FunctionalFlowFuzzer.loadPayloadsForFuzzStep(config, PrettyLoggerFactory.getConsoleLogger()))
                .containsExactly("payload_one");
        var missing = new FunctionalFlowDocument.StepFuzzConfig(List.of("name"), List.of(), List.of(),
                List.of(tempDir.resolve("missing.txt").toString()), FunctionalFlowDocument.FuzzMode.SINGLE);
        Assertions.assertThat(FunctionalFlowFuzzer.loadPayloadsForFuzzStep(missing, PrettyLoggerFactory.getConsoleLogger())).isEmpty();
    }

    @Test
    void shouldRejectMissingFuzzPayloadsAndTargetsWithoutSendingRequests() {
        TestCaseListener listener = flowListener();
        FunctionalFlowFuzzer flow = new FunctionalFlowFuzzer(filesArguments, serviceCaller, listener);
        flow.fuzz(flowData());
        flow.executeDocument(FunctionalFlowParser.parseContent("""
                steps:
                  - POST: /billing-plans
                    fuzz:
                      fields: [name]
                  - POST: /billing-plans
                    fuzz:
                      payloads: [x]
                """), "invalid-config", flow);
        Mockito.verify(listener).recordError(Mockito.contains("payloads or payloadsFile"));
        Mockito.verify(listener).recordError(Mockito.contains("fields or fieldTypes"));
        Mockito.verify(serviceCaller, Mockito.never()).call(Mockito.any());
    }

    @Test
    void shouldSkipFilteredStepsAndAvoidSavingTruncatedResponseValues() {
        TestCaseListener listener = flowListener();
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(false, true);
        FunctionalFlowFuzzer flow = new FunctionalFlowFuzzer(filesArguments, serviceCaller, listener);
        FuzzingData data = flowData();
        flow.fuzz(data);
        CatsResponse truncated = CatsResponse.builder().responseCode(201).body("{\"id\":\"partial\"}").bodyTruncated(true).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(truncated);
        flow.executeDocument(FunctionalFlowParser.parseContent("""
                steps:
                  - name: filtered
                    POST: /billing-plans
                  - name: truncated
                    POST: /billing-plans
                    save:
                      createdId: id
                """), "truncated-flow", flow);
        Mockito.verify(serviceCaller, Mockito.times(1)).call(Mockito.any());
        Mockito.verify(listener).skipTest(Mockito.any(), Mockito.eq("Test skipped due to response code filtering"));
        Mockito.verify(listener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.same(truncated), Mockito.any(), Mockito.anyBoolean(), Mockito.eq(true));
        Assertions.assertThat(flow.getVariables()).doesNotContainKeys("createdId", "steps.filtered.response")
                .containsEntry("steps.truncated.status", "201");
    }

    @ParameterizedTest
    @CsvSource({"true,ORD-123,true", "false,ORD-123,false", "true,INVALID,false"})
    void shouldEvaluateBooleanAndRegexExpectations(boolean flag, String code, boolean matches) {
        TestCaseListener listener = flowListener();
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        FunctionalFlowFuzzer flow = new FunctionalFlowFuzzer(filesArguments, serviceCaller, listener);
        FuzzingData data = flowData();
        flow.fuzz(data);
        String body = "{\"flag\":%s,\"code\":\"%s\"}".formatted(flag, code);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(201)
                .body(body).jsonBody(JsonParser.parseString(body)).build());
        flow.executeDocument(FunctionalFlowParser.parseContent("""
                steps:
                  - POST: /billing-plans
                    status: 201
                    expect:
                      flag: "TRUE"
                      code: 'ORD-\\d+'
                """), "assertions-flow", flow);
        Mockito.verify(serviceCaller).call(Mockito.any());
        if (matches) {
            Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.eq("Response matches all 'verify' parameters"));
            Mockito.verify(listener, Mockito.never()).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString());
        } else {
            Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.eq(data),
                    Mockito.eq("Verify parameters not matching response"), Mockito.contains(flag ? "code" : "flag"));
            Mockito.verify(listener, Mockito.never()).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.anyString());
        }
    }

    @Test
    void shouldSupportDelayMsPollRepeatAndForEachInFunctionalFlows() {
        TestCaseListener listener = flowListener();
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        FunctionalFlowFuzzer flow = new FunctionalFlowFuzzer(filesArguments, serviceCaller, listener);
        FuzzingData data = flowData();
        flow.fuzz(data);

        int[] callCount = {0};
        Mockito.when(serviceCaller.call(Mockito.any())).thenAnswer(inv -> {
            callCount[0]++;
            // Calls 1 & 2: repeat=2 setup + final call (returns 201 on call 1, 429 on call 2)
            if (callCount[0] == 1) {
                return CatsResponse.builder().responseCode(201).body("{\"state\":\"OK\"}")
                        .jsonBody(JsonParser.parseString("{\"state\":\"OK\"}")).build();
            }
            if (callCount[0] == 2) {
                return CatsResponse.builder().responseCode(429).body("{\"state\":\"EXHAUSTED\"}")
                        .jsonBody(JsonParser.parseString("{\"state\":\"EXHAUSTED\"}")).build();
            }
            // Calls 3 & 4: poll attempts (call 3 returns PENDING, call 4 returns COMPLETED)
            if (callCount[0] == 3) {
                return CatsResponse.builder().responseCode(201).body("{\"state\":\"PENDING\"}")
                        .jsonBody(JsonParser.parseString("{\"state\":\"PENDING\"}")).build();
            }
            // Calls 5 & 6: forEach 2 rows
            ServiceData sd = inv.getArgument(0);
            String tier = sd.getDynamicVariables().getOrDefault("tier", "COMPLETED");
            String body = "{\"state\":\"%s\"}".formatted(callCount[0] == 4 ? "COMPLETED" : tier);
            return CatsResponse.builder().responseCode(201).body(body)
                    .jsonBody(JsonParser.parseString(body)).build();
        });

        flow.executeDocument(FunctionalFlowParser.parseContent("""
                steps:
                  - name: exhaustQuotaWithRepeat
                    POST: /billing-plans
                    delayMs: 1
                    repeat: 2
                    expect:
                      status: 429
                      state: EXHAUSTED
                  - name: pollUntilCompleted
                    POST: /billing-plans
                    poll:
                      maxAttempts: 3
                      intervalMs: 1
                    expect:
                      status: 201
                      state: COMPLETED
                  - name: parameterizedTiers
                    POST: /billing-plans
                    forEach:
                      - { tier: GOLD, len: 16, active: true, tags: ["A", "B"], meta: { env: "prod" }, prefix: "G" }
                      - { tier: SILVER, len: 18, active: false, tags: ["C"], meta: { env: "qa" }, prefix: }
                    body:
                      name: "${tier}"
                      active: "${active}"
                      tags: "${tags}"
                      meta: "${meta}"
                      nested:
                        length: "${len}"
                        prefix: "${prefix}"
                    expect:
                      status: 201
                      state: "${tier}"
                """), "advanced-flow", flow);

        // 2 calls for repeat=2 + 2 calls for poll (stopped on 2nd attempt) + 2 calls for forEach (2 rows) = 6 total HTTP calls
        ArgumentCaptor<ServiceData> captor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller, Mockito.times(6)).call(captor.capture());
        Assertions.assertThat(captor.getAllValues().get(0).isExpectsRateLimit()).isTrue();
        Assertions.assertThat(captor.getAllValues().get(1).isExpectsRateLimit()).isTrue();
        Assertions.assertThat(captor.getAllValues().get(2).isExpectsRateLimit()).isFalse();
        Assertions.assertThat(captor.getAllValues().get(4).getPayload())
                .contains("\"length\":16", "\"active\":true", "\"tags\":[\"A\",\"B\"]", "\"meta\":{\"env\":\"prod\"}", "\"prefix\":\"G\"");
        Assertions.assertThat(captor.getAllValues().get(5).getPayload())
                .contains("\"length\":18", "\"active\":false", "\"tags\":[\"C\"]", "\"meta\":{\"env\":\"qa\"}", "\"prefix\":null");
        // 4 reported test cases (1 for repeat, 1 for poll, 2 for forEach rows), 0 errors
        Mockito.verify(listener, Mockito.times(4)).reportResultInfo(
                Mockito.any(), Mockito.eq(data), Mockito.eq("Response matches all 'verify' parameters"));
        Mockito.verify(listener, Mockito.never()).reportResultError(
                Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void shouldSupportDocumentAndStepHeadersOmissionAndStepHeaderReferences() {
        TestCaseListener listener = flowListener();
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        FunctionalFlowFuzzer flow = new FunctionalFlowFuzzer(filesArguments, serviceCaller, listener);
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.POST)
                .path("/billing-plans")
                .contractPath("/billing-plans")
                .payload("{\"name\":\"Plan-1\"}")
                .allRequiredFields(List.of("name"))
                .reqSchema(new ObjectSchema())
                .schemaMap(Map.of())
                .headers(Set.of(
                        CatsHeader.builder().name("X-Contract-Header").value("contract-val").build(),
                        CatsHeader.builder().name("X-Omit-Me").value("drop-me").build()))
                .responseCodes(Set.of("201"))
                .requestContentTypes(List.of("application/json"))
                .build();
        flow.fuzz(data);

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder()
                        .responseCode(201)
                        .body("{\"id\":\"p_1\"}")
                        .jsonBody(JsonParser.parseString("{\"id\":\"p_1\"}"))
                        .headers(List.of(new KeyValuePair<>("ETag", "W/\"v1\""), new KeyValuePair<>("X-Trace", "tr-99")))
                        .build());

        flow.executeDocument(FunctionalFlowParser.parseContent("""
                variables:
                  tenant: "acme"
                headers:
                  X-Tenant: "${tenant}"
                  X-Override: "from-doc"
                steps:
                  - name: createPlan
                    POST: /billing-plans
                    header:
                      X-Override: "from-step"
                      X-Contract-Header: null
                    omit:
                      - "headers.X-Omit-Me"
                    expect:
                      status: 201
                      that:
                        - "response.headers.ETag == 'W/\\\"v1\\\"'"
                        - "headers.x-trace == 'tr-99'"
                  - name: updatePlanWithPreviousHeaders
                    POST: /billing-plans
                    headers:
                      If-Match: "${steps.createPlan.headers.etag}"
                      X-Prev-Req: "$prev.request.headers.X-Override"
                    expect:
                      status: 201
                      that:
                        - "$steps.createPlan.response.headers.X-Trace == 'tr-99'"
                """), "headers-flow", flow);

        ArgumentCaptor<ServiceData> captor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller, Mockito.times(2)).call(captor.capture());

        Map<String, String> step1Headers = captor.getAllValues().get(0).getHeaders().stream()
                .collect(java.util.stream.Collectors.toMap(CatsHeader::getName, CatsHeader::getValue));
        Assertions.assertThat(step1Headers)
                .containsEntry("X-Tenant", "acme")
                .containsEntry("X-Override", "from-step")
                .doesNotContainKeys("X-Contract-Header", "X-Omit-Me");

        Map<String, String> step2Headers = captor.getAllValues().get(1).getHeaders().stream()
                .collect(java.util.stream.Collectors.toMap(CatsHeader::getName, CatsHeader::getValue));
        Assertions.assertThat(step2Headers)
                .containsEntry("X-Tenant", "acme")
                .containsEntry("If-Match", "W/\"v1\"")
                .containsEntry("X-Prev-Req", "from-step");

        Mockito.verify(listener, Mockito.never()).reportResultError(
                Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void shouldExecuteImportedFlowWithTypedVariablesStepTagsAndEnvTypeCasts(@TempDir Path tempDir) throws Exception {
        Path commonDir = Files.createDirectories(tempDir.resolve("common"));
        Path adapterDir = Files.createDirectories(tempDir.resolve("adyen-mock"));
        Files.writeString(commonDir.resolve("flows.yml"), """
                variables:
                  route: "DefaultRoute"
                  expiryMonthExpired: 1
                  refundRequiresAmount: true
                  cardApproved: "4111111111111111"
                  maxRetries: "#(int($$MAX_RETRIES))"
                  apiKey: "$$MOCK_API_KEY"
                steps:
                  - name: auth01Authorize
                    tags: [psp]
                    POST: /billing-plans
                    headers:
                      X-Api-Key: "${apiKey}"
                    body:
                      route: "${route}"
                      expiryMonth: "${expiryMonthExpired}"
                      refundRequiresAmount: "${refundRequiresAmount}"
                      cardNumber: "${cardApproved}"
                      maxRetries: "${maxRetries}"
                      cardAsString: "#(str(${expiryMonthExpired}))"
                    save:
                      expiryMonthExpired: newCode
                    expect:
                      status: 201
                  - name: ops01AdapterCheck
                    tags: [adapter]
                    POST: /billing-plans
                    body:
                      route: "${route}"
                      expiryMonth: "${expiryMonthExpired}"
                    expect:
                      status: 201
                """);

        File adapterFlow = adapterDir.resolve("flows.yml").toFile();
        Files.writeString(adapterFlow.toPath(), """
                imports: ["../common/flows.yml"]
                variables:
                  route: "XeleratorMockECOM"
                  expiryMonthExpired: 12
                  refundRequiresAmount: false
                """);

        TestCaseListener listener = flowListener();
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(serviceCaller.getEnvironmentVariables())
                .thenReturn(Map.of("MAX_RETRIES", "3", "MOCK_API_KEY", "secret-key-val"));
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder().responseCode(201).body("{\"newCode\":\"OVERRIDDEN_STR\"}")
                        .jsonBody(JsonParser.parseString("{\"newCode\":\"OVERRIDDEN_STR\"}")).build());

        File exportRefData = tempDir.resolve("refdata-out.yml").toFile();
        filesArguments.setFunctionalFlowFile(adapterFlow);
        filesArguments.setExportRefDataFile(exportRefData);
        filesArguments.setStepTags(List.of("psp"));

        FunctionalFlowFuzzer flow = new FunctionalFlowFuzzer(filesArguments, serviceCaller, listener);
        flow.fuzz(flowData());
        flow.executeFlows();

        ArgumentCaptor<ServiceData> captor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller, Mockito.times(1)).call(captor.capture());
        String sentPayload = captor.getValue().getPayload();
        Assertions.assertThat(sentPayload)
                .contains("\"route\":\"XeleratorMockECOM\"")
                .contains("\"expiryMonth\":12")
                .contains("\"refundRequiresAmount\":false")
                .contains("\"cardNumber\":\"4111111111111111\"")
                .contains("\"maxRetries\":3")
                .contains("\"cardAsString\":\"12\"");
        Mockito.verify(serviceCaller).registerSensitiveFieldIfPresent("apiKey", "$$MOCK_API_KEY", "secret-key-val");
        Assertions.assertThat(Files.readString(exportRefData.toPath())).contains("expiryMonth: 12");
    }

    @Test
    void shouldFailFastBeforeHttpCallsWhenStepDependsOnTagExcludedStep() {
        TestCaseListener listener = flowListener();
        filesArguments.setStepTags(List.of("adapter"));
        FunctionalFlowFuzzer flow = new FunctionalFlowFuzzer(filesArguments, serviceCaller, listener);
        flow.fuzz(flowData());

        FunctionalFlowDocument doc = FunctionalFlowParser.parseContent("""
                steps:
                  - name: auth01Authorize
                    tags: [psp]
                    POST: /billing-plans
                    save:
                      authRef: id
                  - name: cap01Capture
                    tags: [adapter]
                    POST: /billing-plans
                    body:
                      reference: "${authRef}"
                """);

        Assertions.assertThatThrownBy(() -> flow.executeDocument(doc, "psp-flow.yml", flow))
                .isInstanceOf(com.endava.cats.exception.CatsException.class)
                .hasMessageContaining("authRef")
                .hasMessageContaining("auth01Authorize");
        Mockito.verify(serviceCaller, Mockito.never()).call(Mockito.any());
    }

    @Test
    void shouldSkipExecutionCleanlyWhenNoStepsMatchTagFilter() {
        TestCaseListener listener = flowListener();
        filesArguments.setStepTags(List.of("nonexistent"));
        FunctionalFlowFuzzer flow = new FunctionalFlowFuzzer(filesArguments, serviceCaller, listener);
        flow.fuzz(flowData());

        FunctionalFlowDocument doc = FunctionalFlowParser.parseContent("""
                steps:
                  - name: auth01
                    tags: [psp]
                    POST: /billing-plans
                """);

        flow.executeDocument(doc, "tag-miss.yml", flow);
        Mockito.verify(serviceCaller, Mockito.never()).call(Mockito.any());
    }

    @Test
    void shouldResolveChildSuppliedVariablesAliasesAndNestedMapsInImportedFlows(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("common.yml"), """
                variables:
                  derivedTag: "route-${route}"
                  expiryAlias: "${expiryMonth}"
                  nullVal: null
                  nullAlias: "${nullVal}"
                  nestedConfig:
                    route: "${route}"
                    month: "${expiryMonth}"
                steps:
                  - name: sendNested
                    POST: /billing-plans
                    body:
                      derivedTag: "${derivedTag}"
                      expiryAlias: "${expiryAlias}"
                      nullAlias: "${nullAlias}"
                      nestedConfig: "${nestedConfig}"
                    expect:
                      status: 201
                """);
        File childFlow = tempDir.resolve("child.yml").toFile();
        Files.writeString(childFlow.toPath(), """
                imports: ["common.yml"]
                variables:
                  route: "MockAdyen"
                  expiryMonth: 12
                """);

        TestCaseListener listener = flowListener();
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder().responseCode(201).body("{\"ok\":true}")
                        .jsonBody(JsonParser.parseString("{\"ok\":true}")).build());

        filesArguments.setFunctionalFlowFile(childFlow);
        FunctionalFlowFuzzer flow = new FunctionalFlowFuzzer(filesArguments, serviceCaller, listener);
        flow.fuzz(flowData());
        flow.executeFlows();

        ArgumentCaptor<ServiceData> captor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller, Mockito.times(1)).call(captor.capture());
        Assertions.assertThat(captor.getValue().getPayload())
                .contains("\"derivedTag\":\"route-MockAdyen\"")
                .contains("\"expiryAlias\":12")
                .contains("\"nullAlias\":null")
                .contains("\"nestedConfig\":{\"route\":\"MockAdyen\",\"month\":12}");
    }

    @Test
    void shouldSupportWholeBodyAndHeadersFromVariablesDynamicOmitRemoveSentinelAndFlowLevelStepTags(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("common.yml"), """
                variables:
                  webhookBody:
                    id: "evt_1"
                    type: "charge.captured"
                    nested:
                      dropMe: "cats_remove_field"
                      keepMe: true
                  webhookListBody:
                    - { id: "a" }
                    - { id: "b" }
                  webhookHeaders:
                    X-Signature: "sig-123"
                    X-Drop-Header: null
                  omitList: ["dropByOmit", "headers.X-Omit-Hdr"]
                  singleOmit: "extraOmit"
                  routeVal: "cats_remove_field"
                headers:
                  X-Flow-Default: "from-flow"
                  X-Omit-Hdr: "should-be-omitted"
                steps:
                  - name: webhookObjectStep
                    tags: [psp]
                    POST: /billing-plans
                    base: empty
                    headers: "${webhookHeaders}"
                    body: "${webhookBody}"
                    expect:
                      status: 201
                  - name: webhookListAndOmitStep
                    tags: [psp]
                    POST: /billing-plans
                    headers:
                      X-Flow-Default: "step-wins"
                    body:
                      name: "keep"
                      route: "${routeVal}"
                      nullField: null
                      dropByOmit: "bye"
                      extraOmit: "bye2"
                    omit:
                      - "${omitList}"
                      - "${singleOmit}"
                    expect:
                      status: 201
                  - name: webhookArrayStep
                    tags: [psp]
                    POST: /billing-plans
                    body: "${webhookListBody}"
                    expect:
                      status: 201
                  - name: untaggedLocalStep
                    POST: /billing-plans
                    expect:
                      status: 201
                  - name: adapterOnlyStep
                    tags: [adapter]
                    POST: /billing-plans
                    expect:
                      status: 201
                """);
        File childFlow = tempDir.resolve("child.yml").toFile();
        Files.writeString(childFlow.toPath(), """
                imports: ["common.yml"]
                stepTags: [psp]
                """);

        TestCaseListener listener = flowListener();
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder().responseCode(201).body("{\"ok\":true}")
                        .jsonBody(JsonParser.parseString("{\"ok\":true}")).build());

        filesArguments.setFunctionalFlowFile(childFlow);
        FunctionalFlowFuzzer flow = new FunctionalFlowFuzzer(filesArguments, serviceCaller, listener);
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.POST).path("/billing-plans").contractPath("/billing-plans")
                .payload("{\"name\":\"Default\",\"route\":\"default-route\",\"dropByOmit\":\"init\",\"extraOmit\":\"init2\"}")
                .reqSchema(new ObjectSchema()).schemaMap(Map.of())
                .headers(Set.of(CatsHeader.builder().name("X-Drop-Header").value("old").build()))
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();
        flow.fuzz(data);
        flow.executeFlows();

        ArgumentCaptor<ServiceData> captor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller, Mockito.times(3)).call(captor.capture());

        ServiceData call1 = captor.getAllValues().get(0);
        Assertions.assertThat(call1.getPayload())
                .contains("\"id\":\"evt_1\"")
                .contains("\"type\":\"charge.captured\"")
                .contains("\"keepMe\":true")
                .doesNotContain("dropMe")
                .doesNotContain("id=evt_1");
        Assertions.assertThat(call1.getHeaders())
                .anySatisfy(h -> Assertions.assertThat(h.getName()).isEqualTo("X-Signature")
                        .satisfies(_ -> Assertions.assertThat(h.getValue()).isEqualTo("sig-123")))
                .noneSatisfy(h -> Assertions.assertThat(h.getName()).isEqualToIgnoringCase("X-Drop-Header"));
        Assertions.assertThat(call1.getProtectedHeaders()).contains("x-signature", "x-drop-header", "x-flow-default");

        ServiceData call2 = captor.getAllValues().get(1);
        Assertions.assertThat(call2.getPayload())
                .contains("\"name\":\"keep\"")
                .contains("\"nullField\":null")
                .doesNotContain("route")
                .doesNotContain("dropByOmit")
                .doesNotContain("extraOmit");
        Assertions.assertThat(call2.getHeaders())
                .anySatisfy(h -> Assertions.assertThat(h.getName()).isEqualTo("X-Flow-Default")
                        .satisfies(_ -> Assertions.assertThat(h.getValue()).isEqualTo("step-wins")))
                .noneSatisfy(h -> Assertions.assertThat(h.getName()).isEqualToIgnoringCase("X-Omit-Hdr"));
        Assertions.assertThat(call2.getProtectedHeaders()).contains("x-flow-default", "x-omit-hdr");

        ServiceData call3 = captor.getAllValues().get(2);
        Assertions.assertThat(call3.getPayload()).isEqualTo("[{\"id\":\"a\"},{\"id\":\"b\"}]");

        // CLI --stepTags overrides YAML stepTags: [psp]
        Mockito.reset(serviceCaller);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder().responseCode(201).body("{\"ok\":true}")
                        .jsonBody(JsonParser.parseString("{\"ok\":true}")).build());
        filesArguments.setStepTags(List.of("adapter"));
        flow.fuzz(data);
        flow.executeFlows();
        Mockito.verify(serviceCaller, Mockito.times(1)).call(Mockito.any());
    }

    @Test
    void shouldOmitFieldsHeadersQueryAndListItemsForNullOrUnsetOptionalPlaceholders(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("common.yml"), """
                variables:
                  nestedPayload:
                    route: "${route?}"
                    amount: 100
                steps:
                  - name: payStep
                    POST: /billing-plans
                    headers:
                      X-Route: "${route?}"
                      X-Unset: "${neverDefined?}"
                    query:
                      routeParam: "${route?}"
                      keepParam: "yes"
                    body:
                      processingRouteIdentifier: "${route?}"
                      explicitNull: "${route}"
                      nested:
                        route: "${route?}"
                        keep: true
                      tags: ["always", "${route?}", "${neverDefined?}"]
                      label: "pay-${route?}"
                      nestedVar: "${nestedPayload}"
                    expect:
                      status: 201
                """);
        File stripeFlow = tempDir.resolve("stripe.yml").toFile();
        Files.writeString(stripeFlow.toPath(), """
                imports: ["common.yml"]
                variables:
                  route: null
                """);
        File adyenFlow = tempDir.resolve("adyen.yml").toFile();
        Files.writeString(adyenFlow.toPath(), """
                imports: ["common.yml"]
                variables:
                  route: "XeleratorMockECOM"
                """);

        TestCaseListener listener = flowListener();
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder().responseCode(201).body("{\"ok\":true}")
                        .jsonBody(JsonParser.parseString("{\"ok\":true}")).build());

        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.POST).path("/billing-plans").contractPath("/billing-plans")
                .payload("{\"processingRouteIdentifier\":\"default\",\"nested\":{\"route\":\"default\",\"keep\":false}}")
                .pathParamsPayload("{\"routeParam\":\"default\"}")
                .reqSchema(new ObjectSchema()).schemaMap(Map.of())
                .headers(Set.of(CatsHeader.builder().name("X-Route").value("default").build()))
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();

        // Stripe: route is null -> optional ${route?} omitted everywhere, while non-optional ${route} sends JSON null
        filesArguments.setFunctionalFlowFile(stripeFlow);
        FunctionalFlowFuzzer flow = new FunctionalFlowFuzzer(filesArguments, serviceCaller, listener);
        flow.fuzz(data);
        flow.executeFlows();

        ArgumentCaptor<ServiceData> stripeCaptor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller, Mockito.times(1)).call(stripeCaptor.capture());
        ServiceData stripeCall = stripeCaptor.getValue();
        Assertions.assertThat(stripeCall.getPayload())
                .doesNotContain("processingRouteIdentifier")
                .contains("\"explicitNull\":null")
                .contains("\"nested\":{\"keep\":true}")
                .contains("\"tags\":[\"always\"]")
                .contains("\"label\":\"pay-\"")
                .contains("\"nestedVar\":{\"amount\":100}");
        Assertions.assertThat(stripeCall.getPathParamsPayload())
                .doesNotContain("routeParam")
                .contains("\"keepParam\":\"yes\"");
        Assertions.assertThat(stripeCall.getHeaders())
                .noneSatisfy(h -> Assertions.assertThat(h.getName()).isEqualToIgnoringCase("X-Route"))
                .noneSatisfy(h -> Assertions.assertThat(h.getName()).isEqualToIgnoringCase("X-Unset"));
        Assertions.assertThat(stripeCall.getProtectedHeaders()).contains("x-route", "x-unset");

        // With an Adyen route set, both placeholder forms resolve to the same value.
        Mockito.reset(serviceCaller);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder().responseCode(201).body("{\"ok\":true}")
                        .jsonBody(JsonParser.parseString("{\"ok\":true}")).build());
        filesArguments.setFunctionalFlowFile(adyenFlow);
        flow.fuzz(data);
        flow.executeFlows();

        ArgumentCaptor<ServiceData> adyenCaptor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller, Mockito.times(1)).call(adyenCaptor.capture());
        ServiceData adyenCall = adyenCaptor.getValue();
        Assertions.assertThat(adyenCall.getPayload())
                .contains("\"processingRouteIdentifier\":\"XeleratorMockECOM\"")
                .contains("\"explicitNull\":\"XeleratorMockECOM\"")
                .contains("\"tags\":[\"always\",\"XeleratorMockECOM\"]")
                .contains("\"label\":\"pay-XeleratorMockECOM\"")
                .contains("\"nestedVar\":{\"route\":\"XeleratorMockECOM\",\"amount\":100}");
        Assertions.assertThat(adyenCall.getPathParamsPayload()).contains("\"routeParam\":\"XeleratorMockECOM\"");
        Assertions.assertThat(adyenCall.getHeaders())
                .anySatisfy(h -> Assertions.assertThat(h.getName()).isEqualTo("X-Route")
                        .satisfies(_ -> Assertions.assertThat(h.getValue()).isEqualTo("XeleratorMockECOM")));
    }

    private TestCaseListener flowListener() {
        TestCaseListener listener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return null;
        }).when(listener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        return listener;
    }

    private FuzzingData flowData() {
        return FuzzingData.builder().method(HttpMethod.POST).path("/billing-plans").contractPath("/billing-plans")
                .payload("{\"name\":\"Plan\"}").reqSchema(new ObjectSchema()).schemaMap(Map.of())
                .headers(Set.of()).responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();
    }
}
