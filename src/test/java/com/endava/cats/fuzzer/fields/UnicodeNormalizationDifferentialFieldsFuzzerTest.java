package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class UnicodeNormalizationDifferentialFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private UnicodeNormalizationDifferentialFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        FieldsIteratorExecutor executor = new FieldsIteratorExecutor(serviceCaller, testCaseListener,
                Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        fuzzer = new UnicodeNormalizationDifferentialFieldsFuzzer(testCaseListener, executor);
    }

    @Test
    void shouldHaveDescriptionAndToString() {
        Assertions.assertThat(fuzzer.description()).contains("NFKC", "Best-Fit");
        Assertions.assertThat(fuzzer).hasToString("UnicodeNormalizationDifferentialFieldsFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldSkipNonStringFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("count"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("count", new Schema<>().type("integer")));
        Mockito.when(data.getPayload()).thenReturn("{\"count\": 5}");

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource(value = {"4,12", "1,4", "null,128", "null,null"}, nullValues = "null")
    void shouldSendTwoNormalizationProbesRespectingSchemaBounds(Integer minLength, Integer maxLength) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().body("{}").responseCode(200).build());
        Schema<String> schema = new Schema<String>().type("string").minLength(minLength).maxLength(maxLength);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("name", schema));
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name"));
        Mockito.when(data.getPayload()).thenReturn("{\"name\":\"alice\"}");

        fuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(new ResponseCodeFamilyDynamic(List.of("2XX", "4XX"))));
    }
}
