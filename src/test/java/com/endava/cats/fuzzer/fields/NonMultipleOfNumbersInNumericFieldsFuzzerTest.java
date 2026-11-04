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
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class NonMultipleOfNumbersInNumericFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private NonMultipleOfNumbersInNumericFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        FieldsIteratorExecutor executor = new FieldsIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        fuzzer = new NonMultipleOfNumbersInNumericFieldsFuzzer(executor);
    }

    @Test
    void shouldHaveDescriptionAndToString() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("NonMultipleOfNumbersInNumericFieldsFuzzer");
    }

    @Test
    void shouldEvaluateMultipleOfEligibilityAndCandidateValues() {
        Assertions.assertThat(NonMultipleOfNumbersInNumericFieldsFuzzer.hasViolableMultipleOf(null)).isFalse();
        Assertions.assertThat(NonMultipleOfNumbersInNumericFieldsFuzzer.hasViolableMultipleOf(new StringSchema().multipleOf(BigDecimal.TEN))).isFalse();
        Assertions.assertThat(NonMultipleOfNumbersInNumericFieldsFuzzer.hasViolableMultipleOf(new IntegerSchema().multipleOf(BigDecimal.ONE))).isFalse();

        Schema<?> intMultiple = new IntegerSchema().multipleOf(BigDecimal.valueOf(5)).minimum(BigDecimal.valueOf(10)).maximum(BigDecimal.valueOf(10));
        Assertions.assertThat(NonMultipleOfNumbersInNumericFieldsFuzzer.hasViolableMultipleOf(intMultiple)).isTrue();
        Assertions.assertThat(NonMultipleOfNumbersInNumericFieldsFuzzer.computeNonMultipleValue(intMultiple)).isEqualTo(9L);

        Schema<?> decimalMultiple = new NumberSchema().multipleOf(new BigDecimal("0.5"));
        Assertions.assertThat(NonMultipleOfNumbersInNumericFieldsFuzzer.computeNonMultipleValue(decimalMultiple))
                .isEqualTo(new BigDecimal("0.75"));
    }

    @Test
    void shouldExecuteFuzzingForNumericMultipleOfFields() {
        Schema<?> intSchema = new IntegerSchema().multipleOf(BigDecimal.valueOf(10));
        FuzzingData data = FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.POST)
                .payload("{\"quantity\":20,\"name\":\"item\"}")
                .requestPropertyTypes(Map.of("quantity", intSchema, "name", new StringSchema()))
                .allFieldsByHttpMethod(Set.of("quantity", "name"))
                .requestContentTypes(List.of("application/json"))
                .headers(Set.of())
                .build();

        Mockito.when(serviceCaller.call(Mockito.any()))
                .thenReturn(CatsResponse.builder().responseCode(400).body("{}").build());
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);

        fuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.eq(true), Mockito.eq(true));
    }
}
