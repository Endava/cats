package com.endava.cats.fuzzer.http;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
class BypassAuthenticationFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private CatsUtil catsUtil;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @MockBean
    private BuildProperties buildProperties;


    private BypassAuthenticationFuzzer bypassAuthenticationFuzzer;

    @BeforeEach
    void setup() {
        bypassAuthenticationFuzzer = new BypassAuthenticationFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    void givenAPayloadWithoutAuthenticationHeaders_whenApplyingTheBypassAuthenticationFuzzer_thenTheResultsAreCorrectlyReported() {
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).build();
        bypassAuthenticationFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).skipTest(Mockito.any(), Mockito.anyString());

    }


    @Test
    void givenAPayloadWithAuthenticationHeadersAndCustomHeaders_whenApplyingTheBypassAuthenticationFuzzer_thenTheResultsAreCorrectlyReported() throws Exception {
        ReflectionTestUtils.setField(bypassAuthenticationFuzzer, "headersFile", "notEmpty");
        Mockito.when(catsUtil.parseYaml(Mockito.anyString())).thenReturn(createCustomFuzzerFile());
        bypassAuthenticationFuzzer.loadHeaders();

        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("authorization").value("auth").build())).
                responses(responses).path("test1").build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);
        Mockito.doCallRealMethod().when(catsUtil).powerSet(Mockito.anySet());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        bypassAuthenticationFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX));
    }

    @Test
    void givenAPayloadWithAuthenticationHeaders_whenApplyingTheBypassAuthenticationFuzzer_thenTheResultsAreCorrectlyReported() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("authorization").value("auth").build())).
                responses(responses).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);
        Mockito.doCallRealMethod().when(catsUtil).powerSet(Mockito.anySet());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        bypassAuthenticationFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX));
    }

    @Test
    void givenABypassAuthenticationHeadersFuzzerInstance_whenCallingTheMethodInheritedFromTheBaseClass_thenTheMethodsAreProperlyOverridden() {
        Assertions.assertThat(bypassAuthenticationFuzzer.description()).isNotNull();
        Assertions.assertThat(bypassAuthenticationFuzzer).hasToString(bypassAuthenticationFuzzer.getClass().getSimpleName());
        Assertions.assertThat(bypassAuthenticationFuzzer.skipFor()).isEmpty();
    }

    private Map<String, Map<String, Object>> createCustomFuzzerFile() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        Map<String, Object> path = new HashMap<>();
        Map<String, Object> tests = new HashMap<>();
        tests.put("jwt", "v1");
        tests.put("authorisation", "200");

        path.put("test1", tests);

        result.put("path1", path);
        return result;
    }
}
