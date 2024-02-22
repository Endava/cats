package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
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
class EmptyStringsInFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private FilterArguments filterArguments;
    private FilesArguments filesArguments;

    private EmptyStringsInFieldsFuzzer emptyStringsInFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        filterArguments = Mockito.mock(FilterArguments.class);

        emptyStringsInFieldsFuzzer = new EmptyStringsInFieldsFuzzer(serviceCaller, testCaseListener, filesArguments, filterArguments);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldNotRunForSkippedFields() {
        Mockito.when(filterArguments.getSkipFields()).thenReturn(Collections.singletonList("id"));
        Assertions.assertThat(emptyStringsInFieldsFuzzer.skipForFields()).containsOnly("id");
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("id"));
        Mockito.when(data.getPayload()).thenReturn("{}");
        emptyStringsInFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.any());
    }

    @Test
    void shouldReturnExpectedResultCode4xx() {
        Assertions.assertThat(emptyStringsInFieldsFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(emptyStringsInFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveTypeOfData() {
        Assertions.assertThat(emptyStringsInFieldsFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }

    @Test
    void shouldReturnSkipStrategy() {
        FuzzingStrategy fuzzingStrategy = emptyStringsInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("");
    }

    @Test
    void shouldNotRunFuzzerWhenGetButNoQueryParam() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).queryParams(Set.of("query1")).build();
        Assertions.assertThat(emptyStringsInFieldsFuzzer.isFuzzerWillingToFuzz(data, "notQuery")).isFalse();
    }

    @Test
    void shouldRunFuzzerWhenGetAndQueryParam() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).queryParams(Set.of("query1")).build();
        Assertions.assertThat(emptyStringsInFieldsFuzzer.isFuzzerWillingToFuzz(data, "query1")).isTrue();
    }

    @Test
    void shouldRunFuzzerWhenPost() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.POST).build();
        Assertions.assertThat(emptyStringsInFieldsFuzzer.isFuzzerWillingToFuzz(data, "notQuery")).isTrue();
    }
}
