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
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class ControlCharsInStringFieldsSanitizeValidateFuzzerTest {
    private ControlCharsInStringFieldsSanitizeValidateFuzzer fuzzer;
    private ServiceCaller serviceCaller;
    private FilesArguments filesArguments;
    @InjectSpy
    TestCaseListener testCaseListener;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        Mockito.when(filesArguments.getRefData(Mockito.any())).thenReturn(Map.of());
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        fuzzer = new ControlCharsInStringFieldsSanitizeValidateFuzzer(serviceCaller, testCaseListener, filesArguments);
    }

    @Test
    void shouldHaveDescriptionAndToString() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString(fuzzer.getClass().getSimpleName());
        Assertions.assertThat(fuzzer.shouldCheckForFuzzedValueMatchingPattern()).isFalse();
    }

    @Test
    void shouldFuzzStringFieldsAndExpect2XXEvenWhenPatternPresent() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        StringSchema nameSchema = new StringSchema();
        nameSchema.setPattern("^[a-zA-Z]+$");
        StringSchema enumSchema = new StringSchema();
        enumSchema.setEnum(List.of("A", "B"));
        Map<String, Schema> propTypes = Map.of(
                "name", nameSchema,
                "status", enumSchema,
                "count", new IntegerSchema());
        Mockito.when(data.getPath()).thenReturn("/items");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("name", "status", "count"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"name\":\"Alice\",\"status\":\"A\",\"count\":1}");
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(200).build());

        fuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(5)).reportResult(Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.TWOXX), Mockito.eq(true), Mockito.eq(true));
    }
}
