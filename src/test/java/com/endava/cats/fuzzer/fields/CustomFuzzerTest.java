package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsDSLParser;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.CustomFuzzerUtil;
import com.google.gson.JsonObject;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;

@QuarkusTest
class CustomFuzzerTest {
    @InjectSpy
    private TestCaseListener testCaseListener;

    private FilesArguments filesArguments;
    private ServiceCaller serviceCaller;
    private CatsDSLParser catsDSLParser;
    private CatsUtil catsUtil;
    private CustomFuzzerUtil customFuzzerUtil;
    private CustomFuzzer customFuzzer;

    @BeforeEach
    void setup() {
        catsDSLParser = new CatsDSLParser();
        catsUtil = new CatsUtil(catsDSLParser);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filesArguments = new FilesArguments(catsUtil);
        customFuzzerUtil = new CustomFuzzerUtil(serviceCaller, catsUtil, testCaseListener, catsDSLParser);
        customFuzzer = new CustomFuzzer(filesArguments, customFuzzerUtil);
        filesArguments.getCustomFuzzerDetails().clear();
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldReplaceRefData() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer.yml", "{\"code\": \"200\"}");
        File refData = File.createTempFile("refData", ".yml");
        String line = "this/is/custom/${resp}";
        Files.write(refData.toPath(), line.getBytes());
        ReflectionTestUtils.setField(filesArguments, "refDataFile", refData);

        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        spyCustomFuzzer.replaceRefData();

        File refDataReplaced = new File(refData.getAbsolutePath().substring(0, refData.getAbsolutePath().lastIndexOf(".")) + "_replaced.yml");
        String replaced = Files.readString(refDataReplaced.toPath());

        Assertions.assertThat(replaced).isEqualTo("this/is/custom/200\n");
        refData.deleteOnExit();
        refDataReplaced.deleteOnExit();
    }

    @Test
    void shouldThrowExceptionWhenFileDoesNotExist() throws Exception {
        ReflectionTestUtils.setField(filesArguments, "customFuzzerFile", new File("mumu"));

        Assertions.assertThatThrownBy(() -> filesArguments.loadCustomFuzzerFile()).isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void givenAnEmptyCustomFuzzerFile_whenTheFuzzerRuns_thenNothingHappens() {
        FuzzingData data = FuzzingData.builder().build();
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();

        Mockito.verify(spyCustomFuzzer, Mockito.never()).processCustomFuzzerFile(data);
        Assertions.assertThat(customFuzzer.description()).isNotNull();
        Assertions.assertThat(customFuzzer).hasToString(customFuzzer.getClass().getSimpleName());
        Assertions.assertThat(customFuzzer.reservedWords()).containsOnly(CustomFuzzerUtil.EXPECTED_RESPONSE_CODE, CustomFuzzerUtil.DESCRIPTION, CustomFuzzerUtil.OUTPUT, CustomFuzzerUtil.VERIFY, CustomFuzzerUtil.MAP_VALUES,
                CustomFuzzerUtil.ONE_OF_SELECTION, CustomFuzzerUtil.ADDITIONAL_PROPERTIES, CustomFuzzerUtil.ELEMENT, CustomFuzzerUtil.HTTP_METHOD);
    }

    @Test
    void givenACustomFuzzerFileWithSimpleTestCases_whenTheFuzzerRuns_thenCustomTestCasesAreExecuted() throws Exception {
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("field", "oldValue");

        FuzzingData data = this.setupFuzzingData(catsResponse, jsonObject, "newValue", "newValue2");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();

        Mockito.verify(spyCustomFuzzer, Mockito.times(1)).processCustomFuzzerFile(data);
        Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void givenACustomFuzzerFileWithSimpleTestCases_whenTheFuzzerRuns_thenCustomTestCasesAreExecutedAndDatesAreProperlyParsed() throws Exception {
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("field", "oldValue");

        FuzzingData data = this.setupFuzzingData(catsResponse, jsonObject, "T(java.time.OffsetDateTime).now().plusDays(20)");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();

        Mockito.verify(spyCustomFuzzer, Mockito.times(1)).processCustomFuzzerFile(data);
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    private FuzzingData setupFuzzingData(CatsResponse catsResponse, JsonObject jsonObject, String... customFieldValues) throws IOException {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().path("path1").payload("{\"field\":\"oldValue\"}").
                responses(responses).responseCodes(Collections.singleton("200")).method(HttpMethod.POST).build();

        CatsUtil mockCatsUtil = Mockito.mock(CatsUtil.class);
        Mockito.when(mockCatsUtil.parseYaml(any())).thenReturn(createCustomFuzzerFile(customFieldValues));
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        filesArguments = new FilesArguments(mockCatsUtil);
        customFuzzerUtil = new CustomFuzzerUtil(serviceCaller, mockCatsUtil, testCaseListener, catsDSLParser);
        customFuzzer = new CustomFuzzer(filesArguments, customFuzzerUtil);
        ReflectionTestUtils.setField(filesArguments, "customFuzzerFile", new File("custom"));

        return data;
    }

    @Test
    void givenACompleteCustomFuzzerFileWithDescriptionAndOutputVariables_whenTheFuzzerRuns_thenTheTestCasesAreCorrectlyExecuted() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer.yml", "{\"code\": \"200\"}");

        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(4)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void givenACustomFuzzerFileWithAdditionalProperties_whenTheFuzzerRuns_thenPropertiesAreProperlyAddedToThePayload() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer-additional.yml", "{\"code\": \"200\"}");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void givenACustomFuzzerFileWithVerifyParameters_whenTheFuzzerRuns_thenVerifyParameterAreProperlyChecked() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer-verify.yml", "{\"name\": {\"first\": \"Cats\"}, \"id\": \"25\"}");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(3)).reportInfo(Mockito.any(), Mockito.eq("Response matches all 'verify' parameters"), Mockito.any());
    }

    @Test
    void shouldVerifyWithRequestVariables() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer-req-verify.yml", "{\"name\": {\"first\": \"Cats\"}, \"id\": \"25\", \"pet\":\"myPet\"}");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(PrettyLogger.class), Mockito.eq("Response matches all 'verify' parameters"), Mockito.any());
    }

    @Test
    void givenACustomFuzzerFileWithVerifyParametersThatDoNotMatchTheResponseValues_whenTheFuzzerRuns_thenAnErrorIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer-verify.yml", "{\"name\": {\"first\": \"Cats\"}, \"id\": \"45\"}");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(3)).reportError(Mockito.any(), Mockito.eq("Parameter [id] with value [45] not matching [25]. "), Mockito.any());
    }

    @Test
    void givenACustomFuzzerFile_whenCurrentPathDoesNotExistInFile_thenNoTestIsExecuted() throws Exception {
        setContext("src/test/resources/customFuzzer-verify.yml", "{\"name\": {\"first\": \"Cats\"}, \"id\": \"45\"}");

        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(FuzzingData.builder().path("path1").build());
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void givenACustomFuzzerFileWithVerifyParametersThatAreNotInResponse_whenTheFuzzerRuns_thenAnErrorIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer-verify-not-set.yml", "{\"name\": {\"first\": \"Cats\"}, \"id\": \"25\"}");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.eq("The following Verify parameters were not present in the response: {}"),
                AdditionalMatchers.aryEq(new Object[]{List.of("address")}));
    }

    @Test
    void givenACustomFuzzerFileWithHttpMethodThatIsNotInContract_whenTheFuzzerRuns_thenAnErrorIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/customFuzzer-http-method.yml", "{\"name\": {\"first\": \"Cats\"}, \"id\": \"25\"}");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyCustomFuzzer.fuzz(data);
        spyCustomFuzzer.executeCustomFuzzerTests();
        Mockito.verifyNoInteractions(testCaseListener);
    }

    private FuzzingData setContext(String fuzzerFile, String responsePayload) throws Exception {
        ReflectionTestUtils.setField(filesArguments, "customFuzzerFile", new File(fuzzerFile));
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        CatsResponse catsResponse = CatsResponse.from(200, responsePayload, "POST", 2);

        FuzzingData data = FuzzingData.builder().path("/pets/{id}/move").payload("{\"pet\":\"oldValue\"}").
                responses(responses).responseCodes(Collections.singleton("200")).method(HttpMethod.POST).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        return data;
    }

    private Map<String, Map<String, Object>> createCustomFuzzerFile(String... customFieldValues) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        Map<String, Object> path = new HashMap<>();
        Map<String, Object> tests = new HashMap<>();
        tests.put("k1", "v1");
        tests.put("field", Arrays.asList(customFieldValues));
        tests.put("expectedResponseCode", "200");
        tests.put("httpMethod", "POST");

        path.put("test1", tests);
        path.put("test2", tests);

        result.put("path1", path);
        result.put("path2", Collections.singletonMap("test", "second"));

        return result;
    }
}
