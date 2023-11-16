package com.endava.cats.fuzzer.special;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@QuarkusTest
class SecurityFuzzerTest {
    private FilesArguments filesArguments;
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private CatsUtil catsUtil;
    private CustomFuzzerUtil customFuzzerUtil;

    private SecurityFuzzer securityFuzzer;

    @BeforeEach
    void setup() {
        catsUtil = new CatsUtil();
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filesArguments = new FilesArguments();
        customFuzzerUtil = new CustomFuzzerUtil(serviceCaller, catsUtil, testCaseListener);
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
        Assertions.assertThat(securityFuzzer.reservedWords()).containsOnly(CatsDSLWords.EXPECTED_RESPONSE_CODE, CatsDSLWords.DESCRIPTION, CatsDSLWords.OUTPUT, CatsDSLWords.VERIFY,
                CatsDSLWords.STRINGS_FILE, CatsDSLWords.TARGET_FIELDS, CatsDSLWords.MAP_VALUES, CatsDSLWords.ONE_OF_SELECTION, CatsDSLWords.ADDITIONAL_PROPERTIES,
                CatsDSLWords.ELEMENT, CatsDSLWords.HTTP_METHOD, CatsDSLWords.TARGET_FIELDS_TYPES);
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

    @ParameterizedTest
    @CsvSource({"securityFuzzer-allPaths.yml", "securityFuzzer-all.yml"})
    void shouldRunSecurityTestsWhenAllPathIsUsed(String securityFuzzerFile) throws Exception {
        FuzzingData data = setContext("src/test/resources/" + securityFuzzerFile, "{'name': {'first': 'Cats'}, 'id': '25'}");
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        filesArguments.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(22)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void givenAnInvalidSecurityFuzzerFile_whenTheFuzzerRuns_thenNoResultIsReport() throws Exception {
        FuzzingData data = setContext("src/test/resources/securityFuzzer-invalidStrings.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        filesArguments.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.never()).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void shouldConsiderFieldsWithTypeStringWhenRunningFuzzer() throws Exception {
        FuzzingData data = setContext("src/test/resources/securityFuzzer-fieldTypes.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        data.getHeaders().add(CatsHeader.builder().name("header").value("value").build());
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        filesArguments.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(88)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void shouldConsiderHttpBodyFuzzingWhenRunningFuzzer() throws Exception {
        FuzzingData data = setContext("src/test/resources/securityFuzzer-fieldTypes-http-body.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        data.getHeaders().add(CatsHeader.builder().name("header").value("value").build());
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        filesArguments.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(22)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void shouldReplaceArraysFields() throws Exception {
        FuzzingData data = setContext("src/test/resources/securityFuzzer-arrays.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        data.getHeaders().add(CatsHeader.builder().name("header").value("value").build());
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        filesArguments.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(22)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }


    private FuzzingData setContext(String fuzzerFile, String responsePayload) throws Exception {
        ReflectionTestUtils.setField(filesArguments, "securityFuzzerFile", new File(fuzzerFile));
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        CatsResponse catsResponse = CatsResponse.from(200, responsePayload, "POST", 2);
        Map<String, Schema> properties = new HashMap<>();
        properties.put("firstName", new StringSchema());
        properties.put("lastName", new StringSchema());
        properties.put("age", new IntegerSchema());
        properties.put("city", new StringSchema());
        StringSchema email = new StringSchema();
        email.setFormat("email");
        properties.put("email", email);
        ObjectSchema person = new ObjectSchema();
        person.setProperties(properties);
        FuzzingData data = FuzzingData.builder().path("/pets/{id}/move").payload("{'name':'oldValue', 'firstName':'John','lastName':'Cats','email':'john@yahoo.com', 'arrayField':[5,4]}").
                responses(responses).responseCodes(Collections.singleton("200")).method(HttpMethod.POST).reqSchema(person).headers(new HashSet<>())
                .requestContentTypes(List.of("application/json")).requestPropertyTypes(properties).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        return data;
    }

}
