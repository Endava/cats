package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Set;

@QuarkusTest
class NullValuesInFieldsFuzzerTest {
    private FilterArguments filterArguments;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private NullValuesInFieldsFuzzer nullValuesInFieldsFuzzer;
    private FilesArguments filesArguments;

    @BeforeEach
    void setup() {
        filterArguments = Mockito.mock(FilterArguments.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        nullValuesInFieldsFuzzer = new NullValuesInFieldsFuzzer(null, testCaseListener, filesArguments, filterArguments);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldNotRunForSkippedFields() {
        Mockito.when(filterArguments.getSkipFields()).thenReturn(Collections.singletonList("id"));
        Assertions.assertThat(nullValuesInFieldsFuzzer.skipForFields()).containsOnly("id");
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("id"));
        Mockito.when(data.getPayload()).thenReturn("{}");
        nullValuesInFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.any());
    }

    @Test
    void shouldReturnExpectedResultCode2xx() {
        Assertions.assertThat(nullValuesInFieldsFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.TWOXX);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(nullValuesInFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveTypeOfData() {
        Assertions.assertThat(nullValuesInFieldsFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }

    @Test
    void shouldReturnSkipStrategy() {
        FuzzingStrategy fuzzingStrategy = nullValuesInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isNull();
    }

    @Test
    void shouldNotRunFuzzerWhenGetButNoQueryParam() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).queryParams(Set.of("query1")).build();
        Assertions.assertThat(nullValuesInFieldsFuzzer.isFuzzerWillingToFuzz(data, "notQuery")).isFalse();
    }

    @Test
    void shouldRunFuzzerWhenGetAndQueryParam() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).queryParams(Set.of("query1")).build();
        Assertions.assertThat(nullValuesInFieldsFuzzer.isFuzzerWillingToFuzz(data, "query1")).isTrue();
    }

    @Test
    void shouldRunFuzzerWhenPost() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.POST).build();
        Assertions.assertThat(nullValuesInFieldsFuzzer.isFuzzerWillingToFuzz(data, "notQuery")).isTrue();
    }
}
