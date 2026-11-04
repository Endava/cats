package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsField;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

@QuarkusTest
class ReadOnlyFieldsMassAssignmentFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private ReadOnlyFieldsMassAssignmentFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new ReadOnlyFieldsMassAssignmentFuzzer(simpleExecutor, testCaseListener);
    }

    @Test
    void shouldHaveDescriptionAndToStringAndSkippedMethods() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("ReadOnlyFieldsMassAssignmentFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Test
    void shouldSkipWhenEmptyPayloadOrNoReadOnlyFields() {
        FuzzingData emptyData = FuzzingData.builder().payload("").build();
        fuzzer.fuzz(emptyData);
        Mockito.verifyNoInteractions(serviceCaller);

        CatsField writableField = CatsField.builder().name("name").readOnly(false).schema(new StringSchema()).build();
        FuzzingData noReadOnlyData = FuzzingData.builder()
                .payload("{\"name\":\"Alice\"}")
                .allFieldsAsCatsFields(Set.of(writableField))
                .build();
        fuzzer.fuzz(noReadOnlyData);
        Mockito.verifyNoInteractions(serviceCaller);
    }

    @ParameterizedTest
    @CsvSource({
            "200, '{\"role\":\"cats-readonly-tamper\"}', true",
            "500, '{\"error\":\"boom\"}', true",
            "400, '{\"error\":\"rejected\"}', false",
            "200, '{\"role\":\"USER\"}', false"
    })
    void shouldReportOutcomeBasedOnResponseAndReflection(int statusCode, String responseBody, boolean expectError) {
        CatsField readOnlyRole = CatsField.builder().name("role").readOnly(true).schema(new StringSchema()).build();
        CatsField readOnlyNested = CatsField.builder().name("audit#approved").readOnly(true).schema(new BooleanSchema()).build();
        CatsField readOnlyMissingParent = CatsField.builder().name("missing#score").readOnly(true).schema(new IntegerSchema()).build();

        FuzzingData data = FuzzingData.builder()
                .path("/users")
                .method(HttpMethod.POST)
                .payload("{\"name\":\"Alice\",\"audit\":{}}")
                .allFieldsAsCatsFields(Set.of(readOnlyRole, readOnlyNested, readOnlyMissingParent))
                .requestContentTypes(List.of("application/json"))
                .headers(Set.of())
                .build();

        Mockito.when(serviceCaller.call(Mockito.any()))
                .thenReturn(CatsResponse.builder().responseCode(statusCode).body(responseBody).build());

        fuzzer.fuzz(data);

        if (expectError) {
            Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                    Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        } else {
            Mockito.verify(testCaseListener, Mockito.times(2)).reportResultInfo(
                    Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.any());
        }
    }

    @Test
    void shouldSelectCanaryAndHandleInvalidJson() {
        Assertions.assertThat(ReadOnlyFieldsMassAssignmentFuzzer.selectCanaryValue(new IntegerSchema()))
                .isEqualTo(ReadOnlyFieldsMassAssignmentFuzzer.NUMERIC_CANARY);
        Assertions.assertThat(ReadOnlyFieldsMassAssignmentFuzzer.injectReadOnlyField("not-an-object", "id", "1"))
                .isEmpty();
        Assertions.assertThat(CatsResultFactory.Reason.MASS_ASSIGNMENT_DETECTED.value()).isNotBlank();
    }
}
