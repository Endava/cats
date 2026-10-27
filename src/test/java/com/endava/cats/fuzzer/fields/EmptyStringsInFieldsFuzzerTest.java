package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
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
    void shouldFuzzTheRootPlainTextBody() {
        Mockito.when(filesArguments.getRefData("/text")).thenReturn(Map.of());
        Mockito.when(filterArguments.getSkipFields()).thenReturn(List.of());
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).body("{}").build());
        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.POST)
                .path("/text")
                .headers(Set.of())
                .payload("\"hello\"")
                .reqSchema(new StringSchema())
                .requestPropertyTypes(Map.of("$", new StringSchema()))
                .requestContentTypes(List.of("text/plain"))
                .selectedRequestContentType("text/plain")
                .queryParams(Set.of())
                .responseCodes(Set.of("400"))
                .build();

        emptyStringsInFieldsFuzzer.fuzz(data);

        Mockito.verify(serviceCaller).call(Mockito.argThat(request ->
                request.getMutationTargets().contains(RequestTarget.requestBody()) && request.getResponseValidationFields().isEmpty()));
    }

    @Test
    void shouldReturnExpectedResultCode4xx() {
        Assertions.assertThat(emptyStringsInFieldsFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
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
        FuzzingStrategy fuzzingStrategy = emptyStringsInFieldsFuzzer.getFieldFuzzingStrategy(null, null).getFirst();
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

    @Test
    void shouldExpect4xxOr2xxForOptionalQueryParameter() {
        StringSchema sortSchema = new StringSchema();
        sortSchema.setEnum(List.of("asc", "desc"));
        Mockito.when(filesArguments.getRefData("/evals")).thenReturn(Map.of());
        Mockito.when(filterArguments.getSkipFields()).thenReturn(List.of());
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).body("{}").build());

        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.GET)
                .path("/evals")
                .headers(Set.of())
                .payload("{\"sort_direction\":\"desc\"}")
                .allFields(Set.of("sort_direction"))
                .allRequiredFields(List.of())
                .reqSchema(new StringSchema())
                .requestPropertyTypes(Map.of("sort_direction", sortSchema))
                .requestContentTypes(List.of("application/json"))
                .queryParams(Set.of("sort_direction"))
                .responseCodes(Set.of("200", "400"))
                .build();

        emptyStringsInFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener).reportResult(
                Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldExpect4xxForOptionalBodyEnumFieldWithoutEmptyStringInEnum() {
        StringSchema modeSchema = new StringSchema();
        modeSchema.setEnum(List.of("SINGLE", "RECURRING"));
        Mockito.when(filesArguments.getRefData("/orders")).thenReturn(Map.of());
        Mockito.when(filterArguments.getSkipFields()).thenReturn(List.of());
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(422).body("{}").build());

        FuzzingData data = FuzzingData.builder()
                .method(HttpMethod.POST)
                .path("/orders")
                .headers(Set.of())
                .payload("{\"mode\":\"SINGLE\"}")
                .allFields(Set.of("mode"))
                .allRequiredFields(List.of())
                .reqSchema(new StringSchema())
                .requestPropertyTypes(Map.of("mode", modeSchema))
                .requestContentTypes(List.of("application/json"))
                .queryParams(Set.of())
                .responseCodes(Set.of("200", "422"))
                .build();

        emptyStringsInFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener).reportResult(
                Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());

        Mockito.reset(testCaseListener);
        modeSchema.setDefault("SINGLE");
        emptyStringsInFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener).reportResult(
                Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.eq(ResponseCodeFamilyPredefined.TWOXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }
}
