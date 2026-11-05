package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class TimezoneAndLocaleCodeFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private TimezoneAndLocaleCodeFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.when(testCaseListener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        FieldsIteratorExecutor executor = new FieldsIteratorExecutor(
                serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        fuzzer = new TimezoneAndLocaleCodeFieldsFuzzer(executor);
    }

    @ParameterizedTest
    @CsvSource({
            "timeZone,Invalid/Timezone",
            "tz,UTC+25:00",
            "locale,xx-INVALID-999",
            "language_code,en_US@currency=INVALID",
            "currencyCode,ZZZ",
            "country_code,INVALID_ISO"
    })
    void shouldSelectCategorySpecificPayloads(String fieldName, String expectedPayload) {
        Assertions.assertThat(TimezoneAndLocaleCodeFieldsFuzzer.isTargetFieldName(fieldName)).isTrue();
        Assertions.assertThat(TimezoneAndLocaleCodeFieldsFuzzer.payloadsForField(fieldName)).contains(expectedPayload);
    }

    @Test
    void shouldFuzzNonEnumTimezoneAndCurrencyFields() {
        CatsResponse response = CatsResponse.builder().responseCode(400).body("{}").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response);

        StringSchema enumCurrency = new StringSchema();
        enumCurrency.setEnum(List.of("USD", "EUR"));

        FuzzingData data = FuzzingData.builder()
                .path("/preferences")
                .method(HttpMethod.POST)
                .reqSchema(new Schema<>())
                .requestContentTypes(List.of("application/json"))
                .payload("{\"timeZone\":\"Europe/Bucharest\",\"currency\":\"USD\",\"name\":\"Alice\"}")
                .allFieldsByHttpMethod(Set.of("timeZone", "currency", "name"))
                .requestPropertyTypes(Map.of(
                        "timeZone", new StringSchema(),
                        "currency", enumCurrency,
                        "name", new StringSchema()))
                .build();

        fuzzer.fuzz(data);

        Mockito.verify(serviceCaller, Mockito.times(2)).call(Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.eq(data), Mockito.eq(response), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @Test
    void shouldReturnDescriptionSkipMethodsAndToString() {
        Assertions.assertThat(fuzzer.description()).contains("timezone, locale, currency, and country code");
        Assertions.assertThat(fuzzer.skipForHttpMethods()).containsExactly(HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("TimezoneAndLocaleCodeFieldsFuzzer");
    }
}
