package com.endava.cats.fuzzer;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
public class HappyFuzzerTest {

    @MockBean
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @MockBean
    private BuildProperties buildProperties;

    private HappyFuzzer happyFuzzer;


    @BeforeEach
    public void setup() {
        happyFuzzer = new HappyFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    public void givenARequest_whenCallingTheHappyFuzzer_thenTestCasesAreCorrectlyExecuted() {
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().path("path1").method(HttpMethod.POST).payload("{'field':'oldValue'}").
                responses(responses).responseCodes(Collections.singleton("200")).build();

        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);

        happyFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
        Assertions.assertThat(happyFuzzer.description()).isNotNull();
        Assertions.assertThat(happyFuzzer).hasToString(happyFuzzer.getClass().getSimpleName());
    }

    @Test
    public void givenARequest_whenCallingTheHappyFuzzerAndAnErrorOccurs_thenTestCasesAreCorrectlyExecuted() {
        FuzzingData data = FuzzingData.builder().path("path1").method(HttpMethod.POST).payload("{'field':'oldValue'}").responseCodes(Collections.singleton("200")).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenThrow(new RuntimeException());

        happyFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.anyString(), Mockito.any());
    }
}
