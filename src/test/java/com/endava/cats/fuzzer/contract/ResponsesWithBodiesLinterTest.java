package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

@QuarkusTest
class ResponsesWithBodiesLinterTest {

    private TestCaseListener testCaseListener;

    private ResponsesWithBodiesLinter responsesWithBodiesLinterFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        responsesWithBodiesLinterFuzzer = new ResponsesWithBodiesLinter(testCaseListener);
    }

    @Test
    void shouldReturnInfo() {
        FuzzingData data = FuzzingData.builder().responses(Map.of("200", List.of("1", "2"))).build();
        responsesWithBodiesLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All HTTP response codes have a response body"));
    }

    @Test
    void shouldReturnError() {
        FuzzingData data = FuzzingData.builder().responses(Map.of("200", List.of())).build();
        responsesWithBodiesLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(),
                Mockito.eq("Missing response body for some HTTP response codes"), Mockito.eq("The following HTTP response codes are missing a response body: {}"), Mockito.eq(List.of("200")));
    }

    @ParameterizedTest
    @CsvSource({"204", "304"})
    void shouldSkipCodes(String code) {
        FuzzingData data = FuzzingData.builder().responses(Map.of(code, List.of())).build();
        responsesWithBodiesLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All HTTP response codes have a response body"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(responsesWithBodiesLinterFuzzer).hasToString(responsesWithBodiesLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(responsesWithBodiesLinterFuzzer.description()).isEqualTo("verifies that HTTP response codes (except for 204 and 304) have a response body");
    }

    @Test
    void shouldSkipForHttpHead() {
        Assertions.assertThat(responsesWithBodiesLinterFuzzer.skipForHttpMethods()).containsOnly(HttpMethod.HEAD);
    }
}
