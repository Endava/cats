package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

@QuarkusTest
class ZeroWidthCharsInValuesFieldsSanitizeValidateFuzzerTest {
    private ZeroWidthCharsInValuesFieldsSanitizeValidateFuzzer zeroWidthCharsInValuesFieldsSanitizeValidateFuzzer;
    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        zeroWidthCharsInValuesFieldsSanitizeValidateFuzzer = new ZeroWidthCharsInValuesFieldsSanitizeValidateFuzzer(serviceCaller, testCaseListener, Mockito.mock(FilesArguments.class));
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(zeroWidthCharsInValuesFieldsSanitizeValidateFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(zeroWidthCharsInValuesFieldsSanitizeValidateFuzzer).hasToString(zeroWidthCharsInValuesFieldsSanitizeValidateFuzzer.getClass().getSimpleName());
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "null"}, nullValues = "null")
    void shouldNotRunWithEmptyPayload(String payload) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn(payload);
        zeroWidthCharsInValuesFieldsSanitizeValidateFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(serviceCaller);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldNotRunWhenNoFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of());
        zeroWidthCharsInValuesFieldsSanitizeValidateFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(serviceCaller);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldRunWhenFieldsPresent() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name", "lastName", "address#zip"));
        Mockito.when(data.getPayload()).thenReturn("""
                {
                    "name": "John",
                    "lastName": "Doe",
                    "address": {
                        "zip": "12345"
                    }
                }
                """);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());
        zeroWidthCharsInValuesFieldsSanitizeValidateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(18)).reportResult(Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.TWOXX), Mockito.eq(true), Mockito.eq(true));
    }

    @Test
    void shouldNotRunWhenFieldIsADiscriminator() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name", "lastName", "address#zip"));
        Mockito.when(data.getPayload()).thenReturn("""
                {
                    "name": "John",
                    "lastName": "Doe",
                    "address": {
                        "zip": "12345"
                    }
                }
                """);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(false);
        zeroWidthCharsInValuesFieldsSanitizeValidateFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(serviceCaller);
    }
}
