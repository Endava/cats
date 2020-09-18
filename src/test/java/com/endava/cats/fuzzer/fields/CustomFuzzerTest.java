package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.CustomFuzzerUtil;
import com.google.gson.JsonObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(SpringExtension.class)
class CustomFuzzerTest {
    @MockBean
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;

    @MockBean
    private CatsUtil catsUtil;

    @SpyBean
    private CustomFuzzerUtil customFuzzerUtil;

    private CustomFuzzer customFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
        customFuzzer = new CustomFuzzer(catsUtil, customFuzzerUtil);
        Mockito.when(buildProperties.getName()).thenReturn("CATS");
        Mockito.when(buildProperties.getVersion()).thenReturn("1.1");
        Mockito.when(buildProperties.getTime()).thenReturn(Instant.now());
        ReflectionTestUtils.setField(testCaseListener, "buildProperties", buildProperties);
    }


    @Test
    void givenAnEmptyCustomFuzzerFile_whenTheFuzzerRuns_thenNothingHappens() {
        FuzzingData data = FuzzingData.builder().build();
        ReflectionTestUtils.setField(customFuzzer, "customFuzzerFile", "empty");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();

        Mockito.verify(spyCustomFuzzer, Mockito.never()).processCustomFuzzerFile(data);
        Assertions.assertThat(customFuzzer.description()).isNotNull();
        Assertions.assertThat(customFuzzer).hasToString(customFuzzer.getClass().getSimpleName());
        Assertions.assertThat(customFuzzer.reservedWords()).containsOnly(CustomFuzzerUtil.EXPECTED_RESPONSE_CODE, CustomFuzzerUtil.DESCRIPTION, CustomFuzzerUtil.OUTPUT, CustomFuzzerUtil.VERIFY);
    }

    @Test
    void givenACustomFuzzerFileWithSimpleTestCases_whenTheFuzzerRuns_thenCustomTestCasesAreExecuted() throws Exception {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().path("path1").payload("{'field':'oldValue'}").
                responses(responses).responseCodes(Collections.singleton("200")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("field", "oldValue");

        ReflectionTestUtils.setField(customFuzzer, "customFuzzerFile", "custom");
        Mockito.when(catsUtil.parseYaml(any())).thenReturn(createCustomFuzzerFile());
        Mockito.when(catsUtil.parseAsJsonElement(data.getPayload())).thenReturn(jsonObject);
        Mockito.when(catsUtil.getJsonElementBasedOnFullyQualifiedName(Mockito.eq(jsonObject), Mockito.eq("field"))).thenReturn(jsonObject);
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        spyCustomFuzzer.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();

        Mockito.verify(spyCustomFuzzer, Mockito.times(1)).processCustomFuzzerFile(data);
        Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
        Assertions.assertThat(jsonObject.toString()).contains("newValue");
    }

    @Test
    void givenACompleteCustomFuzzerFileWithDescriptionAndOutputVariables_whenTheFuzzerRuns_thenTheTestCasesAreCorrectlyExecuted() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer.yml", "{'code': '200'}");

        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        spyCustomFuzzer.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(4)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void givenACustomFuzzerFileWithVerifyParameters_whenTheFuzzerRuns_thenVerifyParameterAreProperlyChecked() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer-verify.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        spyCustomFuzzer.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(3)).reportInfo(Mockito.any(), Mockito.eq("Response matches all 'verify' parameters"));
    }

    @Test
    void givenACustomFuzzerFileWithVerifyParametersThatDoNotMatchTheResponseValues_whenTheFuzzerRuns_thenAnErrorIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer-verify.yml", "{'name': {'first': 'Cats'}, 'id': '45'}");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        spyCustomFuzzer.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(3)).reportError(Mockito.any(), Mockito.eq("Parameter [id] with value [45] not matching [25]. "));
    }

    @Test
    void givenACustomFuzzerFileWithVerifyParametersThatAreNotInResponse_whenTheFuzzerRuns_thenAnErrorIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer-verify-not-set.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        spyCustomFuzzer.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.eq("The following Verify parameters were not present in the response: {}"),
                Mockito.eq(Collections.singletonList("address")));
    }

    @Test
    void givenACustomFuzzerFileWithHttpMethodThatIsNotInContract_whenTheFuzzerRuns_thenAnErrorIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer-http-method.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        spyCustomFuzzer.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verifyNoInteractions(testCaseListener);
    }

    private FuzzingData setContext(String fuzzerFile, String responsePayload) throws Exception {
        ReflectionTestUtils.setField(customFuzzer, "customFuzzerFile", fuzzerFile);
        Mockito.doCallRealMethod().when(catsUtil).parseYaml(fuzzerFile);
        Mockito.doCallRealMethod().when(catsUtil).parseAsJsonElement(Mockito.anyString());
        Mockito.doCallRealMethod().when(catsUtil).getJsonElementBasedOnFullyQualifiedName(Mockito.any(), Mockito.anyString());
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        CatsResponse catsResponse = CatsResponse.from(200, responsePayload, "POST");

        FuzzingData data = FuzzingData.builder().path("/pets/{id}/move").payload("{'pet':'oldValue'}").
                responses(responses).responseCodes(Collections.singleton("200")).method(HttpMethod.POST).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);

        return data;
    }

    private Map<String, Map<String, Object>> createCustomFuzzerFile() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        Map<String, Object> path = new HashMap<>();
        Map<String, Object> tests = new HashMap<>();
        tests.put("k1", "v1");
        tests.put("field", Arrays.asList("newValue", "newValue2"));
        tests.put("expectedResponseCode", "200");

        path.put("test1", tests);
        path.put("test2", tests);

        result.put("path1", path);
        return result;
    }
}
