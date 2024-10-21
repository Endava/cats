package com.endava.cats.fuzzer.fields;

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
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

@QuarkusTest
class InsertWhitespacesInFieldNamesFieldFuzzerTest {
    @InjectSpy
    private TestCaseListener testCaseListener;
    private ServiceCaller serviceCaller;

    private InsertWhitespacesInFieldNamesFieldFuzzer insertWhitespacesInFieldNamesFieldFuzzer;

    private CatsResponse catsResponse;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        insertWhitespacesInFieldNamesFieldFuzzer = new InsertWhitespacesInFieldNamesFieldFuzzer(serviceCaller, testCaseListener);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldNotRunForEmptyPayload() {
        insertWhitespacesInFieldNamesFieldFuzzer.fuzz(Mockito.mock(FuzzingData.class));

        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldNotRunForFieldNotInPayload() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"field1\": \"value1\"}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("field2"));
        insertWhitespacesInFieldNamesFieldFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldRunWhenFieldInPayload() {
        catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"field1\": \"value1\"}");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("field1"));
        insertWhitespacesInFieldNamesFieldFuzzer.fuzz(data);

        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @Test
    void shouldHaveProperDescriptionAndToString() {
        Assertions.assertThat(insertWhitespacesInFieldNamesFieldFuzzer.description()).isNotNull();
        Assertions.assertThat(insertWhitespacesInFieldNamesFieldFuzzer).hasToString(insertWhitespacesInFieldNamesFieldFuzzer.getClass().getSimpleName());
    }
}
