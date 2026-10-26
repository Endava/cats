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
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.ObjectSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.AdditionalMatchers;
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
        File refDataTemplate = tempDir.resolve("refData.yml").toFile();
        Files.writeString(refDataTemplate.toPath(), "/billing-plans/{planId}:\n  planId: ${createdPlanId}\n");

        filesArguments.setFunctionalFlowFile(flowFile);
        filesArguments.setRefDataFile(refDataTemplate);

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

        File replacedRefData = tempDir.resolve("refData_replaced.yml").toFile();
        Assertions.assertThat(replacedRefData).exists();
        Assertions.assertThat(Files.readString(replacedRefData.toPath())).contains("planId: plan_42");
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
        filesArguments.setCreateRefData(true);
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
                """;
        File flowFile = tempDir.resolve("missing-and-warn.yml").toFile();
        Files.writeString(flowFile.toPath(), flowYaml);
        filesArguments.setFunctionalFlowFile(flowFile);

        FuzzingData postPlan = FuzzingData.builder()
                .method(HttpMethod.POST).path("/billing-plans").contractPath("/billing-plans")
                .payload("{\"name\":\"Plan\"}").reqSchema(new ObjectSchema()).schemaMap(Map.of()).headers(Set.of())
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();
        fuzzer.fuzz(postPlan);

        String respBody = "{\"id\":\"plan_99\"}";
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
        Files.writeString(flowFile.toPath(), flowYaml);
        filesArguments.setFunctionalFlowFile(flowFile);
        filesArguments.setCreateRefData(true);
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
        File customRef = new File("refData_custom.yml");
        Assertions.assertThat(customRef).exists();
        customRef.deleteOnExit();
    }
}
