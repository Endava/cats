package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
class RemoveHeadersFuzzerTest {
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


    private RemoveHeadersFuzzer removeHeadersFuzzer;

    @BeforeEach
    void setup() {
        removeHeadersFuzzer = new RemoveHeadersFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    void givenASetOfHeadersWithNoRequiredHeaders_whenApplyingTheRemoveHeadersFuzzer_thenTheHeadersAreProperlyFuzzed() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).
                responses(responses).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);
        Mockito.when(catsUtil.getExpectedWordingBasedOnRequiredFields(Mockito.eq(false))).thenReturn(new Object[]{ResponseCodeFamily.TWOXX, "were not"});
        Mockito.when(catsUtil.getResultCodeBasedOnRequiredFieldsRemoved(Mockito.eq(false))).thenReturn(ResponseCodeFamily.TWOXX);

        Mockito.doCallRealMethod().when(catsUtil).powerSet(Mockito.anySet());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        removeHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void givenASetOfHeadersWithRequiredHeaders_whenApplyingTheRemoveHeadersFuzzer_thenTheHeadersAreProperlyFuzzed() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("400", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").required(true).build())).
                responses(responses).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);
        Mockito.when(catsUtil.getExpectedWordingBasedOnRequiredFields(Mockito.eq(false))).thenReturn(new Object[]{ResponseCodeFamily.TWOXX, "were not"});
        Mockito.when(catsUtil.getResultCodeBasedOnRequiredFieldsRemoved(Mockito.eq(false))).thenReturn(ResponseCodeFamily.TWOXX);
        Mockito.when(catsUtil.getExpectedWordingBasedOnRequiredFields(Mockito.eq(true))).thenReturn(new Object[]{ResponseCodeFamily.FOURXX, "were"});
        Mockito.when(catsUtil.getResultCodeBasedOnRequiredFieldsRemoved(Mockito.eq(true))).thenReturn(ResponseCodeFamily.FOURXX);
        Mockito.doCallRealMethod().when(catsUtil).powerSet(Mockito.anySet());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        removeHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX));
    }

    @Test
    void givenASetOfHeaders_whenAnErrorOccursCallingTheService_thenTheErrorIsProperlyReported() {
        FuzzingData data = FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenThrow(new RuntimeException());
        Mockito.when(catsUtil.getExpectedWordingBasedOnRequiredFields(Mockito.eq(false))).thenReturn(new Object[]{ResponseCodeFamily.TWOXX, "were not"});
        Mockito.when(catsUtil.getResultCodeBasedOnRequiredFieldsRemoved(Mockito.eq(false))).thenReturn(ResponseCodeFamily.TWOXX);
        Mockito.doCallRealMethod().when(catsUtil).powerSet(Mockito.anySet());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any());

        removeHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(2)).reportError(Mockito.any(), Mockito.anyString(), Mockito.any());
    }

    @Test
    void givenARemoveHeadersFuzzerInstance_whenCallingTheMethodInheritedFromTheBaseClass_thenTheMethodsAreProperlyOverridden() {
        Assertions.assertThat(removeHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(removeHeadersFuzzer).hasToString(removeHeadersFuzzer.getClass().getSimpleName());
        Assertions.assertThat(removeHeadersFuzzer.skipFor()).isEmpty();
    }
}
