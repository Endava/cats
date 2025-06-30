package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@QuarkusTest
class AdditionalPropertiesMassAssignmentFieldsFuzzerTest {
    @InjectSpy
    TestCaseListener testCaseListener;
    FieldsIteratorExecutor catsExecutor;
    private AdditionalPropertiesMassAssignmentFieldsFuzzer additionalPropertiesMassAssignmentFieldsFuzzer;

    @BeforeEach
    void setup() {
        catsExecutor = new FieldsIteratorExecutor(Mockito.mock(ServiceCaller.class), testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        additionalPropertiesMassAssignmentFieldsFuzzer = new AdditionalPropertiesMassAssignmentFieldsFuzzer(catsExecutor, Mockito.mock(ProcessingArguments.class));
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(additionalPropertiesMassAssignmentFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(additionalPropertiesMassAssignmentFieldsFuzzer).hasToString("AdditionalPropertiesMassAssignmentFieldsFuzzer");
    }

    @Test
    void shouldSkipForNonBodyMethods() {
        Assertions.assertThat(additionalPropertiesMassAssignmentFieldsFuzzer.skipForHttpMethods())
                .contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD);
    }

    @Test
    void shouldSkipIfNoAdditionalProperties() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("field", Mockito.mock(Schema.class)));
        Mockito.when(data.getPayload()).thenReturn("""
                    {"field": "value"}
                """);
        additionalPropertiesMassAssignmentFieldsFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldRunIfAdditionalPropertiesPresent() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("field"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of("field", new Schema().additionalProperties(false)));
        Mockito.when(data.getPayload()).thenReturn("""
                   {"field": {"property" : "value", "additionalProperty": "value"}}
                """);

        additionalPropertiesMassAssignmentFieldsFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(any(), eq(data), any(), any());
    }
}
