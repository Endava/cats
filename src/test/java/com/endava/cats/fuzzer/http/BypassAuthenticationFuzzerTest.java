package com.endava.cats.fuzzer.http;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
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
    private CatsUtil catsUtil;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;

    private BypassAuthenticationFuzzer bypassAuthenticationFuzzer;

    @BeforeEach
    void setup() {
        catsUtil = Mockito.mock(CatsUtil.class);
        filesArguments = new FilesArguments(catsUtil);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        bypassAuthenticationFuzzer = new BypassAuthenticationFuzzer(serviceCaller, testCaseListener, filesArguments);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void givenAPayloadWithoutAuthenticationHeaders_whenApplyingTheBypassAuthenticationFuzzer_thenTheFuzzerIsSkipped() {
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).build();
        bypassAuthenticationFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).skipTest(Mockito.any(), Mockito.anyString());
    }


    @Test
    void givenAPayloadWithAuthenticationHeadersAndCustomHeaders_whenApplyingTheBypassAuthenticationFuzzer_thenTheFuzzerRuns() throws Exception {
        ReflectionTestUtils.setField(filesArguments, "headersFile", new File("notEmpty"));
        Mockito.when(catsUtil.parseYaml(Mockito.anyString())).thenReturn(createCustomFuzzerFile());
        filesArguments.loadHeaders();

        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("authorization").value("auth").build())).
                responses(responses).path("test1").build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        bypassAuthenticationFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX_AA));
    }

    @Test
    void givenAPayloadWithAuthenticationHeaders_whenApplyingTheBypassAuthenticationFuzzer_thenTheFuzzerRuns() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("authorization").value("auth").build())).
                responses(responses).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        bypassAuthenticationFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX_AA));
    }

    @Test
    void givenABypassAuthenticationHeadersFuzzerInstance_whenCallingTheMethodInheritedFromTheBaseClass_thenTheMethodsAreProperlyOverridden() {
        Assertions.assertThat(bypassAuthenticationFuzzer.description()).isNotNull();
        Assertions.assertThat(bypassAuthenticationFuzzer).hasToString(bypassAuthenticationFuzzer.getClass().getSimpleName());
        Assertions.assertThat(bypassAuthenticationFuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldProperlyIdentifyAuthHeadersFromContract() {
        List<CatsHeader> headers = Arrays.asList(CatsHeader.builder().name("jwt").build(), CatsHeader.builder().name("authorization").build(),
                CatsHeader.builder().name("api-key").build(), CatsHeader.builder().name("api_key").build(), CatsHeader.builder().name("cats").build());
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(headers)).build();

        Set<String> authHeaders = bypassAuthenticationFuzzer.getAuthenticationHeaderProvided(data);
        Assertions.assertThat(authHeaders).containsExactlyInAnyOrder("jwt", "api-key", "authorization", "api_key");
    }

    @Test
    void shouldProperlyIdentifyAuthHeadersFromHeadersFile() throws Exception {
        ReflectionTestUtils.setField(filesArguments, "headersFile", new File("notEmpty"));
        Mockito.when(catsUtil.parseYaml(Mockito.anyString())).thenReturn(createCustomFuzzerFile());
        Mockito.doCallRealMethod().when(catsUtil).loadFileToMap(Mockito.anyString(), Mockito.anyMap());
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>()).path("path1").build();

        filesArguments.loadHeaders();
        Set<String> authHeaders = bypassAuthenticationFuzzer.getAuthenticationHeaderProvided(data);
        Assertions.assertThat(authHeaders).containsExactlyInAnyOrder("api-key", "authorization", "jwt");
    }

    private Map<String, Map<String, Object>> createCustomFuzzerFile() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        Map<String, Object> tests = new HashMap<>();
        tests.put("jwt", "v1");
        tests.put("authorization", "200");
        tests.put("cats", "mumu");

        result.put("all", tests);
        result.put("path1", Collections.singletonMap("api-key", "secret"));
        result.put("path2", Collections.singletonMap("api_key", "secret"));

        return result;
    }
}
