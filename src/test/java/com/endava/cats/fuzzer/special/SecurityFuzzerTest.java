package com.endava.cats.fuzzer.special;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.util.CatsDSLWords;
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
    private CustomFuzzerUtil customFuzzerUtil;

    private SecurityFuzzer securityFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filesArguments = new FilesArguments();
        customFuzzerUtil = new CustomFuzzerUtil(serviceCaller, testCaseListener);
        securityFuzzer = new SecurityFuzzer(filesArguments, customFuzzerUtil);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
    }

    @Test
    void shouldThrowExceptionWhenFileDoesNotExist() {
        ReflectionTestUtils.setField(filesArguments, "securityFuzzerFile", new File("mumu"));

        Assertions.assertThatThrownBy(() -> filesArguments.loadSecurityFuzzerFile()).isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void givenAnEmptySecurityFuzzerFile_whenTheFuzzerRuns_thenNothingHappens() {
        FuzzingData data = FuzzingData.builder().build();
        SecurityFuzzer spyCustomFuzzer = Mockito.spy(securityFuzzer);
        spyCustomFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldOverrideMethods() {
        Assertions.assertThat(securityFuzzer.description()).isNotNull();
        Assertions.assertThat(securityFuzzer).hasToString(securityFuzzer.getClass().getSimpleName());
        Assertions.assertThat(securityFuzzer.requiredKeywords()).containsOnly(CatsDSLWords.EXPECTED_RESPONSE_CODE, CatsDSLWords.HTTP_METHOD);
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
        Mockito.verify(testCaseListener, Mockito.times(1)).recordError("is missing the following mandatory entries: [httpMethod, targetFields or targetFieldTypes]");
    }

    @ParameterizedTest
    @CsvSource({"securityFuzzer-allPaths.yml", "securityFuzzer-all.yml"})
    void shouldRunSecurityTestsWhenAllPathIsUsed(String securityFuzzerFile) throws Exception {
        FuzzingData data = setContext("src/test/resources/" + securityFuzzerFile, "{'name': {'first': 'Cats'}, 'id': '25'}");
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        filesArguments.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(22)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.argThat(arg -> arg.asString().equalsIgnoreCase("200")));
    }

    @Test
    void givenAnInvalidSecurityFuzzerFile_whenTheFuzzerRuns_thenNoResultIsReport() throws Exception {
        FuzzingData data = setContext("src/test/resources/securityFuzzer-invalidStrings.yml", "{'name': {'first': 'Cats'}, 'id': '25'}");
        filesArguments.loadSecurityFuzzerFile();
        securityFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.never()).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.TWOXX));
    }

    @ParameterizedTest
    @CsvSource({"src/test/resources/securityFuzzer-fieldTypes.yml,88", "src/test/resources/securityFuzzer-fieldTypes-http-body.yml,22", "src/test/resources/securityFuzzer-arrays.yml,22"})
    void shouldProperlyParseFieldTypesAndExecuteTests(String file, int expectedTestRuns) throws Exception {
        FuzzingData data = setContext(file, "{'name': {'first': 'Cats'}, 'id': '25'}");
        data.getHeaders().add(CatsHeader.builder().name("header").value("value").build());
        SecurityFuzzer spySecurityFuzzer = Mockito.spy(securityFuzzer);
        filesArguments.loadSecurityFuzzerFile();
        spySecurityFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(expectedTestRuns)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.argThat(arg -> arg.asString().equalsIgnoreCase("200")));
    }

    private FuzzingData setContext(String fuzzerFile, String responsePayload) {
        ReflectionTestUtils.setField(filesArguments, "securityFuzzerFile", new File(fuzzerFile));
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        CatsResponse catsResponse = CatsResponse.builder()
                .responseCode(200).body(responsePayload).httpMethod("POST")
                .responseTimeInMs(2)
                .responseContentType("application/json")
                .build();
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
        FuzzingData data = FuzzingData.builder().path("/pets/{id}/move").contractPath("/pets/{id}/move").payload("{'name':'oldValue', 'firstName':'John','lastName':'Cats','email':'john@yahoo.com', 'arrayField':[5,4]}").
                responses(responses).responseCodes(Collections.singleton("200")).method(HttpMethod.POST).reqSchema(person).headers(new HashSet<>())
                .requestContentTypes(List.of("application/json")).responseContentTypes(Collections.singletonMap("200", Collections.singletonList("application/json"))).requestPropertyTypes(properties).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        return data;
    }

}
