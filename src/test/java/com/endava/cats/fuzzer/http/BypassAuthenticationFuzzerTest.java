package com.endava.cats.fuzzer.http;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class BypassAuthenticationFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private SimpleExecutor simpleExecutor;
    private BypassAuthenticationFuzzer bypassAuthenticationFuzzer;

    @BeforeEach
    void setup() {
        filesArguments = Mockito.mock(FilesArguments.class);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        bypassAuthenticationFuzzer = new BypassAuthenticationFuzzer(simpleExecutor, filesArguments);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
    }

    @Test
    void givenAPayloadWithoutAuthenticationHeaders_whenApplyingTheBypassAuthenticationFuzzer_thenTheFuzzerIsSkipped() {
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).reqSchema(new StringSchema()).build();
        bypassAuthenticationFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(testCaseListener);
    }


    @Test
    void givenAPayloadWithAuthenticationHeadersAndCustomHeaders_whenApplyingTheBypassAuthenticationFuzzer_thenTheFuzzerRuns() {
        Mockito.when(filesArguments.getHeaders(Mockito.anyString())).thenReturn(createCustomFuzzerFile());

        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("authorization").value("auth").build())).
                responses(responses).path("test1").reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).responseCodes(Set.of("400")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any(), Mockito.anyBoolean());

        bypassAuthenticationFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_AA), Mockito.anyBoolean(), Mockito.eq(true));
    }

    @Test
    void givenAPayloadWithAuthenticationHeaders_whenApplyingTheBypassAuthenticationFuzzer_thenTheFuzzerRuns() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("authorization").value("auth").build())).
                responses(responses).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).responseCodes(Set.of("400")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any(), Mockito.anyBoolean());

        bypassAuthenticationFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_AA), Mockito.anyBoolean(), Mockito.eq(true));
    }

    @Test
    void shouldNotSkipForAnyHttpMethod() {
        Assertions.assertThat(bypassAuthenticationFuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(bypassAuthenticationFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(bypassAuthenticationFuzzer).hasToString(bypassAuthenticationFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldProperlyIdentifyAuthHeadersFromContract() {
        List<CatsHeader> headers = Arrays.asList(CatsHeader.builder().name("jwt").build(), CatsHeader.builder().name("authorization").build(),
                CatsHeader.builder().name("api-key").build(), CatsHeader.builder().name("api_key").build(), CatsHeader.builder().name("cats").build());
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(headers)).reqSchema(new StringSchema()).build();

        Set<String> authHeaders = bypassAuthenticationFuzzer.getAuthenticationHeaderProvided(data);
        Assertions.assertThat(authHeaders).containsExactlyInAnyOrder("jwt", "api-key", "authorization", "api_key");
    }

    @Test
    void shouldProperlyIdentifyAuthHeadersFromHeadersFile() {
        Mockito.when(filesArguments.getHeaders(Mockito.any())).thenReturn(createCustomFuzzerFile());
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>()).path("path1").reqSchema(new StringSchema()).build();
        Set<String> authHeaders = bypassAuthenticationFuzzer.getAuthenticationHeaderProvided(data);
        Assertions.assertThat(authHeaders).containsExactlyInAnyOrder("api-key", "authorization", "jwt");
    }

    private Map<String, Object> createCustomFuzzerFile() {
        Map<String, Object> tests = new HashMap<>();
        tests.put("jwt", "v1");
        tests.put("authorization", "200");
        tests.put("cats", "mumu");
        tests.put("api-key", "secret");


        return tests;
    }
}
