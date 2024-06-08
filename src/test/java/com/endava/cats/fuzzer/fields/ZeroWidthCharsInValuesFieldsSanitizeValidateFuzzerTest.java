package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
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
class ZeroWidthCharsInValuesFieldsFuzzerTest {
    private ZeroWidthCharsInValuesFieldsFuzzer zeroWidthCharsInValuesFieldsFuzzer;
    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
        zeroWidthCharsInValuesFieldsFuzzer = new ZeroWidthCharsInValuesFieldsFuzzer(serviceCaller, testCaseListener, Mockito.mock(FilesArguments.class));
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(zeroWidthCharsInValuesFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(zeroWidthCharsInValuesFieldsFuzzer).hasToString(zeroWidthCharsInValuesFieldsFuzzer.getClass().getSimpleName());
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "null"}, nullValues = "null")
    void shouldNotRunWithEmptyPayload(String payload) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn(payload);
        zeroWidthCharsInValuesFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(serviceCaller);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldNotRunWhenNoFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of());
        zeroWidthCharsInValuesFieldsFuzzer.fuzz(data);
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
        zeroWidthCharsInValuesFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(18)).reportResult(Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.eq(true), Mockito.eq(true));
    }
}
