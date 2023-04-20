package com.endava.cats.fuzzer.special;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@QuarkusTest
class FunctionalFuzzerTest {
    @InjectSpy
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private ServiceCaller serviceCaller;
    private CatsDSLParser catsDSLParser;
    private CatsUtil catsUtil;
    private CustomFuzzerUtil customFuzzerUtil;
    private FunctionalFuzzer functionalFuzzer;

    @BeforeEach
    void setup() {
        catsDSLParser = new CatsDSLParser();
        catsUtil = new CatsUtil(catsDSLParser);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filesArguments = new FilesArguments();
        customFuzzerUtil = new CustomFuzzerUtil(serviceCaller, catsUtil, testCaseListener, catsDSLParser);
        functionalFuzzer = new FunctionalFuzzer(filesArguments, customFuzzerUtil);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldReplaceRefData() throws Exception {
        FuzzingData data = setContext("src/test/resources/functionalFuzzer.yml", "{\"code\": \"200\"}");
        File refData = File.createTempFile("refData", ".yml");
        String line = "this/is/custom/${resp}";
        Files.writeString(refData.toPath(), line);
        ReflectionTestUtils.setField(filesArguments, "refDataFile", refData);

        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();
        spyFunctionalFuzzer.replaceRefData();

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
        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();

        Mockito.verify(spyFunctionalFuzzer, Mockito.never()).processCustomFuzzerFile(data);
        Assertions.assertThat(functionalFuzzer.description()).isNotNull();
        Assertions.assertThat(functionalFuzzer).hasToString(functionalFuzzer.getClass().getSimpleName());
        Assertions.assertThat(functionalFuzzer.reservedWords()).containsOnly(CatsDSLWords.EXPECTED_RESPONSE_CODE, CatsDSLWords.DESCRIPTION, CatsDSLWords.OUTPUT, CatsDSLWords.VERIFY, CatsDSLWords.MAP_VALUES,
                CatsDSLWords.ONE_OF_SELECTION, CatsDSLWords.ADDITIONAL_PROPERTIES, CatsDSLWords.ELEMENT, CatsDSLWords.HTTP_METHOD);
    }

    @Test
    void givenACustomFuzzerFileWithSimpleTestCases_whenTheFuzzerRuns_thenCustomTestCasesAreExecuted() throws Exception {
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        FuzzingData data = this.setupFuzzingData(catsResponse, "newValue", "newValue2");
        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();

        Mockito.verify(spyFunctionalFuzzer, Mockito.times(1)).processCustomFuzzerFile(data);
        Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void givenACustomFuzzerFileWithSimpleTestCases_whenTheFuzzerRuns_thenCustomTestCasesAreExecutedAndDatesAreProperlyParsed() throws Exception {
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        FuzzingData data = this.setupFuzzingData(catsResponse, "T(java.time.OffsetDateTime).now().plusDays(20)");
        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();

        Mockito.verify(spyFunctionalFuzzer, Mockito.times(1)).processCustomFuzzerFile(data);
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    private FuzzingData setupFuzzingData(CatsResponse catsResponse, String... customFieldValues) throws IOException {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().path("path1").payload("{\"field\":\"oldValue\"}").
                responses(responses).responseCodes(Collections.singleton("200")).reqSchema(new StringSchema()).method(HttpMethod.POST)
                .headers(new HashSet<>()).requestContentTypes(List.of("application/json")).build();

        CatsUtil mockCatsUtil = Mockito.mock(CatsUtil.class);
        ReflectionTestUtils.setField(filesArguments, "customFuzzerDetails", createCustomFuzzerFile(customFieldValues));
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        customFuzzerUtil = new CustomFuzzerUtil(serviceCaller, mockCatsUtil, testCaseListener, catsDSLParser);
        functionalFuzzer = new FunctionalFuzzer(filesArguments, customFuzzerUtil);
        ReflectionTestUtils.setField(filesArguments, "customFuzzerFile", new File("custom"));

        return data;
    }

    @ParameterizedTest
    @CsvSource({"src/test/resources/functionalFuzzer.yml,TWOXX,4", "src/test/resources/functionalFuzzer-no-resp-code.yml,FOURXX,1", "src/test/resources/functionalFuzzer-resp-code-family.yml,TWOXX_GENERIC,1"})
    void shouldMatchExpectedResultCodeFamily(String file, ResponseCodeFamily expectedResponseCode, int times) throws Exception {
        FuzzingData data = setContext(file, "{\"code\": \"200\"}");

        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(times)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(expectedResponseCode));
    }

    @Test
    void shouldWarnWhenRespCodeNotMatches() throws Exception {
        FuzzingData data = setContext("src/test/resources/functionalFuzzer-resp-code-verify.yml", "{\"code\": \"200\"}");

        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResultWarn(Mockito.any(), Mockito.eq(data), Mockito.eq("Returned response code not matching expected response code"),
                        Mockito.eq("Response matches all 'verify' parameters, but response code doesn't match expected response code: expected [{}], actual [{}]"),
                        AdditionalMatchers.aryEq(new Object[]{"400", "200"}));
    }

    @Test
    void shouldInfoWhenResponseCodeMatchesAsRanges() throws Exception {
        FuzzingData data = setContext("src/test/resources/functionalFuzzer-resp-code-family-verify.yml", "{\"code\": \"200\"}");

        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.eq("Response matches all 'verify' parameters"), Mockito.any());
    }

    @Test
    void shouldParseRequestFields() throws Exception {
        FuzzingData data = setContext("src/test/resources/functionalFuzzer.yml", "{\"code\": \"200\"}");

        filesArguments.loadCustomFuzzerFile();
        functionalFuzzer.fuzz(data);
        functionalFuzzer.executeCustomFuzzerTests();
        Map<String, String> variables = customFuzzerUtil.getVariables();
        Assertions.assertThat(variables).containsEntry("resp", "200").containsEntry("custId", "john");
    }

    @Test
    void shouldWriteRefData() throws Exception {
        FuzzingData data = setContext("src/test/resources/functionalFuzzer.yml", "{\"code\": \"200\"}");
        ReflectionTestUtils.setField(filesArguments, "createRefData", true);
        ReflectionTestUtils.setField(filesArguments, "refDataFile", null);

        filesArguments.loadCustomFuzzerFile();
        functionalFuzzer.fuzz(data);
        functionalFuzzer.executeCustomFuzzerTests();
        functionalFuzzer.replaceRefData();
        File refDataCustom = new File("refData_custom.yml");
        List<String> lines = Files.readAllLines(refDataCustom.toPath());
        Assertions.assertThat(lines).contains("/pets/{id}/move:", "  pet: \"200\"");
        refDataCustom.deleteOnExit();
    }

    @Test
    void givenACustomFuzzerFileWithAdditionalProperties_whenTheFuzzerRuns_thenPropertiesAreProperlyAddedToThePayload() throws Exception {
        FuzzingData data = setContext("src/test/resources/functionalFuzzer-additional.yml", "{\"code\": \"200\"}");
        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void givenACustomFuzzerFileWithVerifyParameters_whenTheFuzzerRuns_thenVerifyParameterAreProperlyChecked() throws Exception {
        FuzzingData data = setContext("src/test/resources/functionalFuzzer-verify.yml", "{\"name\": {\"first\": \"dodo\"}, \"id\": \"25\", \"expiry\":\"2000-02-02\"}");
        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(3)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Response matches all 'verify' parameters"), Mockito.any());
    }

    @Test
    void shouldVerifyWithRequestVariables() throws Exception {
        FuzzingData data = setContext("src/test/resources/functionalFuzzer-req-verify.yml", "{\"name\": {\"first\": \"Cats\"}, \"id\": \"25\", \"code\":\"150\",\"petName\":\"myPet\"}");
        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Response matches all 'verify' parameters"), Mockito.any());
    }

    @Test
    void givenACustomFuzzerFileWithVerifyParametersThatDoNotMatchTheResponseValues_whenTheFuzzerRuns_thenAnErrorIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/functionalFuzzer-verify.yml", "{\"name\": {\"first\": \"dodo\"}, \"id\": \"45\", \"expiry\":\"2000-02-02\"}");
        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(3)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.eq("Parameter [id] with value [45] not matching [25]. "), Mockito.any());
    }

    @Test
    void givenACustomFuzzerFile_whenCurrentPathDoesNotExistInFile_thenNoTestIsExecuted() throws Exception {
        setContext("src/test/resources/functionalFuzzer-verify.yml", "{\"name\": {\"first\": \"Cats\"}, \"id\": \"45\"}");

        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyFunctionalFuzzer.fuzz(FuzzingData.builder().path("path1").build());
        spyFunctionalFuzzer.executeCustomFuzzerTests();
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void givenACustomFuzzerFileWithVerifyParametersThatAreNotInResponse_whenTheFuzzerRuns_thenAnErrorIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/functionalFuzzer-verify-not-set.yml", "{\"name\": {\"first\": \"Cats\"}, \"id\": \"25\"}");
        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.eq("The following Verify parameters were not present in the response: {}"),
                AdditionalMatchers.aryEq(new Object[]{List.of("address")}));
    }

    @Test
    void givenACustomFuzzerFileWithHttpMethodThatIsNotInContract_whenTheFuzzerRuns_thenAnErrorIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/functionalFuzzer-http-method.yml", "{\"name\": {\"first\": \"Cats\"}, \"id\": \"25\"}");
        FunctionalFuzzer spyFunctionalFuzzer = Mockito.spy(functionalFuzzer);
        filesArguments.loadCustomFuzzerFile();
        spyFunctionalFuzzer.fuzz(data);
        spyFunctionalFuzzer.executeCustomFuzzerTests();
        Mockito.verifyNoInteractions(testCaseListener);
    }

    private FuzzingData setContext(String fuzzerFile, String responsePayload) throws Exception {
        ReflectionTestUtils.setField(filesArguments, "customFuzzerFile", new File(fuzzerFile));
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        CatsResponse catsResponse = CatsResponse.from(200, responsePayload, "POST", 2);

        FuzzingData data = FuzzingData.builder().path("/pets/{id}/move").payload("{\"pet\":\"oldValue\", \"name\":\"dodo\"}").
                responses(responses).responseCodes(Collections.singleton("200")).reqSchema(new StringSchema()).method(HttpMethod.POST)
                .headers(new HashSet<>())
                .requestContentTypes(List.of("application/json")).build();
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
