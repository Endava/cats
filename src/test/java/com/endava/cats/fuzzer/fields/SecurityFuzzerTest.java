package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsDSLParser;
import com.endava.cats.util.CatsParams;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.CustomFuzzerUtil;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
class SecurityFuzzerTest {
    @MockBean
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @SpyBean
    private CatsDSLParser catsDSLParser;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;

    @MockBean
    private CatsUtil catsUtil;

    @SpyBean
    private CatsParams catsParams;

    @SpyBean
    private CustomFuzzerUtil customFuzzerUtil;

    private SecurityFuzzer securityFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
        securityFuzzer = new SecurityFuzzer(catsParams, customFuzzerUtil);
        Mockito.when(buildProperties.getName()).thenReturn("CATS");
        Mockito.when(buildProperties.getVersion()).thenReturn("1.1");
        Mockito.when(buildProperties.getTime()).thenReturn(Instant.now());
        ReflectionTestUtils.setField(testCaseListener, "buildProperties", buildProperties);
    }

    @Test
    void shouldThrowExceptionWhenFileDoesNotExist() throws Exception {
        ReflectionTestUtils.setField(catsParams, "securityFuzzerFile", "mumu");
        Mockito.doCallRealMethod().when(catsUtil).parseYaml("mumu");

        catsParams.loadSecurityFuzzerFile();
        SecurityFuzzer spyCustomFuzzer = Mockito.spy(securityFuzzer);
        spyCustomFuzzer.fuzz(FuzzingData.builder().build());

        Mockito.verify(spyCustomFuzzer, Mockito.never()).processSecurityFuzzerFile(Mockito.any());
    }

    @Test
    void givenAnEmptySecurityFuzzerFile_whenTheFuzzerRuns_thenNothingHappens() {
        FuzzingData data = FuzzingData.builder().build();
        ReflectionTestUtils.setField(catsParams, "securityFuzzerFile", "empty");
        SecurityFuzzer spyCustomFuzzer = Mockito.spy(securityFuzzer);
        spyCustomFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(testCaseListener);
        Assertions.assertThat(securityFuzzer.description()).isNotNull();
        Assertions.assertThat(securityFuzzer).hasToString(securityFuzzer.getClass().getSimpleName());
        Assertions.assertThat(securityFuzzer.reservedWords()).containsOnly(CustomFuzzerUtil.EXPECTED_RESPONSE_CODE, CustomFuzzerUtil.DESCRIPTION, CustomFuzzerUtil.OUTPUT, CustomFuzzerUtil.VERIFY,
                CustomFuzzerUtil.STRINGS_FILE, CustomFuzzerUtil.TARGET_FIELDS, CustomFuzzerUtil.MAP_VALUES, CustomFuzzerUtil.ONE_OF_SELECTION, CustomFuzzerUtil.ADDITIONAL_PROPERTIES, CustomFuzzerUtil.ELEMENT, CustomFuzzerUtil.HTTP_METHOD);
    }

    @Test
    void givenASecurityFuzzerFileWithAPathThatIsNotInContract_whenTheFuzzerRuns_thenAnErrorIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/securityFuzzer.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        catsParams.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void givenASecurityFuzzerFileWithMissingReservedWords_whenTheFuzzerRuns_thenAnErrorIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/securityFuzzer-missing.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        catsParams.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void givenASecurityFuzzerFileAllDetailsIn_whenTheFuzzerRuns_thenOnlyInfoIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/securityFuzzer-all.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        catsParams.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(21)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }


    @Test
    void givenAnInvalidSecurityFuzzerFile_whenTheFuzzerRuns_thenNoResultIsReport() throws Exception {
        FuzzingData data = setContext("src/test/resources/securityFuzzer-invalidStrings.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        catsParams.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.never()).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    private FuzzingData setContext(String fuzzerFile, String responsePayload) throws Exception {
        ReflectionTestUtils.setField(catsParams, "securityFuzzerFile", fuzzerFile);
        Mockito.doCallRealMethod().when(catsUtil).parseYaml(fuzzerFile);
        Mockito.doCallRealMethod().when(catsUtil).parseAsJsonElement(Mockito.anyString());
        Mockito.doCallRealMethod().when(catsUtil).replaceField(Mockito.anyString(), Mockito.anyString(), Mockito.any());
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        CatsResponse catsResponse = CatsResponse.from(200, responsePayload, "POST", 2);

        FuzzingData data = FuzzingData.builder().path("/pets/{id}/move").payload("{'name':'oldValue'}").
                responses(responses).responseCodes(Collections.singleton("200")).method(HttpMethod.POST).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);

        return data;
    }

}
