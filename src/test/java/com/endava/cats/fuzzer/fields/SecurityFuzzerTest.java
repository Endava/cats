package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.CustomFuzzerUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
class SecurityFuzzerTest {
    private FilesArguments filesArguments;
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private CatsDSLParser catsDSLParser;
    private CatsUtil catsUtil;
    private CustomFuzzerUtil customFuzzerUtil;

    private SecurityFuzzer securityFuzzer;

    @BeforeEach
    void setup() {
        catsDSLParser = new CatsDSLParser();
        catsUtil = new CatsUtil(catsDSLParser);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filesArguments = new FilesArguments(catsUtil);
        customFuzzerUtil = new CustomFuzzerUtil(serviceCaller, catsUtil, testCaseListener, catsDSLParser);
        securityFuzzer = new SecurityFuzzer(filesArguments, customFuzzerUtil);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldThrowExceptionWhenFileDoesNotExist() throws Exception {
        ReflectionTestUtils.setField(filesArguments, "securityFuzzerFile", new File("mumu"));

        Assertions.assertThatThrownBy(() -> filesArguments.loadSecurityFuzzerFile()).isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void givenAnEmptySecurityFuzzerFile_whenTheFuzzerRuns_thenNothingHappens() {
        FuzzingData data = FuzzingData.builder().build();
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
        filesArguments.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void givenASecurityFuzzerFileWithMissingReservedWords_whenTheFuzzerRuns_thenAnErrorIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/securityFuzzer-missing.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        filesArguments.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void givenASecurityFuzzerFileAllDetailsIn_whenTheFuzzerRuns_thenOnlyInfoIsReported() throws Exception {
        FuzzingData data = setContext("src/test/resources/securityFuzzer-all.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        filesArguments.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(21)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }


    @Test
    void givenAnInvalidSecurityFuzzerFile_whenTheFuzzerRuns_thenNoResultIsReport() throws Exception {
        FuzzingData data = setContext("src/test/resources/securityFuzzer-invalidStrings.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        filesArguments.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.never()).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    private FuzzingData setContext(String fuzzerFile, String responsePayload) throws Exception {
        ReflectionTestUtils.setField(filesArguments, "securityFuzzerFile", new File(fuzzerFile));
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        CatsResponse catsResponse = CatsResponse.from(200, responsePayload, "POST", 2);

        FuzzingData data = FuzzingData.builder().path("/pets/{id}/move").payload("{'name':'oldValue'}").
                responses(responses).responseCodes(Collections.singleton("200")).method(HttpMethod.POST).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        return data;
    }

}
