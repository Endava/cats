package com.endava.cats.fuzzer.special;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsConfiguration;
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
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
                  prefix: "Rule"
                correlate:
                  - paths: ["/risk-rules*"]
                    when: "ruleType == 'STATIC'"
                    then:
                      omit: ["metricField"]
                steps:
                  - name: Create static rule
                    POST: /risk-rules
                    headers:
                      X-Correlation-Id: "#(uuid)"
                    omit: [id]
                    body:
                      ruleType: "STATIC"
                      name: "${prefix}-Alpha"
                      metadata:
                        owner: "risk-team"
                    status: 201
                    save:
                      createdRuleId: ruleId
                    expect:
                      ruleType: "STATIC"
                      headers:
                        X-Server: "risk"
                      bodyContains: ["rule_42"]
                      where:
                        - "priority >= 10 & $request.ruleType = 'STATIC'"

                  - name: Fetch and patch created rule
                    PATCH: /risk-rules/${createdRuleId}?dryRun=false
                    body:
                      active: false
                    status: 200
                    expect:
                      ruleId: "${createdRuleId}"
                      active: false
                """;
        File flowFile = tempDir.resolve("flow.yml").toFile();
        Files.writeString(flowFile.toPath(), yaml);
        File refDataTemplate = tempDir.resolve("refData.yml").toFile();
        Files.writeString(refDataTemplate.toPath(), "/risk-rules/{ruleId}:\n  ruleId: ${createdRuleId}\n");

        filesArguments.setFunctionalFlowFile(flowFile);
        filesArguments.setRefDataFile(refDataTemplate);

        FuzzingData postRules = FuzzingData.builder()
                .method(HttpMethod.POST)
                .path("/risk-rules")
                .contractPath("/risk-rules")
                .payload("{\"id\":\"remove-me\",\"ruleType\":\"STATIC\",\"name\":\"Default\",\"metricField\":\"drop\",\"metadata\":{}}")
                .allRequiredFields(List.of("ruleType", "name"))
                .reqSchema(new ObjectSchema())
                .schemaMap(Map.of())
                .headers(Set.of(CatsHeader.builder().name("X-Correlation-Id").value("old").build()))
                .responseCodes(Set.of("201"))
                .requestContentTypes(List.of("application/json"))
                .build();

        FuzzingData patchRule = FuzzingData.builder()
                .method(HttpMethod.PATCH)
                .path("/risk-rules/{ruleId}")
                .contractPath("/risk-rules/{ruleId}")
                .payload("{\"active\":true}")
                .pathParamsPayload("{\"ruleId\":\"RANDOM\"}")
                .queryParams(Set.of("dryRun"))
                .allRequiredFields(List.of())
                .reqSchema(new ObjectSchema())
                .schemaMap(Map.of())
                .headers(Set.of())
                .responseCodes(Set.of("200"))
                .requestContentTypes(List.of("application/json"))
                .build();

        fuzzer.fuzz(postRules);
        fuzzer.fuzz(patchRule);

        List<ServiceData> capturedCalls = new ArrayList<>();
        Mockito.when(serviceCaller.call(Mockito.any())).thenAnswer(inv -> {
            ServiceData sd = inv.getArgument(0);
            capturedCalls.add(sd);
            if (sd.getHttpMethod() == HttpMethod.POST) {
                String body = "{\"ruleId\":\"rule_42\",\"ruleType\":\"STATIC\",\"priority\":100}";
                return CatsResponse.builder()
                        .responseCode(201)
                        .body(body)
                        .jsonBody(JsonParser.parseString(body))
                        .headers(List.of(new KeyValuePair<>("X-Server", "risk")))
                        .build();
            }
            String body = "{\"ruleId\":\"rule_42\",\"active\":false}";
            return CatsResponse.builder()
                    .responseCode(200)
                    .body(body)
                    .jsonBody(JsonParser.parseString(body))
                    .headers(List.of())
                    .build();
        });

        fuzzer.executeFlows();

        Assertions.assertThat(capturedCalls).hasSize(2);
        ServiceData postCall = capturedCalls.get(0);
        Assertions.assertThat(postCall.getPayload())
                .contains("\"name\":\"Rule-Alpha\"")
                .contains("\"owner\":\"risk-team\"")
                .doesNotContain("\"id\"")
                .doesNotContain("metricField");
        Assertions.assertThat(postCall.getHeaders())
                .anySatisfy(h -> Assertions.assertThat(h.getValue()).matches("[0-9a-f\\-]{36}"));

        ServiceData patchCall = capturedCalls.get(1);
        Assertions.assertThat(patchCall.getPathParamsPayload())
                .contains("\"ruleId\":\"rule_42\"")
                .contains("\"dryRun\":\"false\"");
        Assertions.assertThat(patchCall.getPayload()).contains("\"active\":false");

        File replacedRefData = tempDir.resolve("refData_replaced.yml").toFile();
        Assertions.assertThat(replacedRefData).exists();
        Assertions.assertThat(Files.readString(replacedRefData.toPath())).contains("ruleId: rule_42");
        Assertions.assertThat(fuzzer).hasToString("FunctionalFlowFuzzer");
        Assertions.assertThat(fuzzer.description()).isNotBlank();
    }

    @Test
    void shouldSelectVariantAndReportAssertionFailures(@TempDir Path tempDir) throws Exception {
        String yaml = """
                steps:
                  - name: Create velocity rule
                    POST: /risk-rules
                    variant: VelocityRule
                    base: empty
                    body:
                      ruleType: "VELOCITY"
                    status: 201
                    expect:
                      ruleType: "STATIC"
                      headers:
                        X-Missing: "val"
                      bodyContains: ["must_exist"]
                      bodyNotContains: ["VELOCITY"]
                      where:
                        - "score > 50"
                """;
        File flowFile = tempDir.resolve("flow-fail.yml").toFile();
        Files.writeString(flowFile.toPath(), yaml);
        filesArguments.setFunctionalFlowFile(flowFile);

        FuzzingData staticVariant = FuzzingData.builder()
                .method(HttpMethod.POST).path("/risk-rules").contractPath("/risk-rules")
                .reqSchemaName("StaticRule").payload("{\"ruleType\":\"STATIC\"}")
                .reqSchema(new ObjectSchema()).schemaMap(Map.of()).headers(Set.of())
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();
        FuzzingData velocityVariant = FuzzingData.builder()
                .method(HttpMethod.POST).path("/risk-rules").contractPath("/risk-rules")
                .reqSchemaName("VelocityRule").payload("{\"ruleType\":\"VELOCITY\"}")
                .reqSchema(new ObjectSchema()).schemaMap(Map.of()).headers(Set.of())
                .responseCodes(Set.of("201")).requestContentTypes(List.of("application/json")).build();

        fuzzer.fuzz(staticVariant);
        fuzzer.fuzz(velocityVariant);

        String respBody = "{\"ruleType\":\"VELOCITY\",\"score\":10}";
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                CatsResponse.builder().responseCode(201).body(respBody).jsonBody(JsonParser.parseString(respBody)).headers(List.of()).build());

        fuzzer.executeFlows();

        Mockito.verify(testCaseListener).reportResultError(
                Mockito.any(), Mockito.eq(velocityVariant),
                Mockito.eq("Verify parameters not matching response"),
                Mockito.contains("score > 50"), Mockito.any());
    }
}
