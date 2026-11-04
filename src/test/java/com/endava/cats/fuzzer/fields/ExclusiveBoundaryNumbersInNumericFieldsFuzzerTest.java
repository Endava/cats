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
class ExclusiveBoundaryNumbersInNumericFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private ExclusiveBoundaryNumbersInNumericFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        FieldsIteratorExecutor executor = new FieldsIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        fuzzer = new ExclusiveBoundaryNumbersInNumericFieldsFuzzer(executor);
    }

    @Test
    void shouldHaveDescriptionAndToString() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("ExclusiveBoundaryNumbersInNumericFieldsFuzzer");
    }

    @Test
    void shouldExtractOpenApi30And31ExclusiveBoundaries() {
        Schema<?> oas30 = new IntegerSchema()
                .minimum(BigDecimal.ZERO).exclusiveMinimum(true)
                .maximum(BigDecimal.TEN).exclusiveMaximum(true);
        Assertions.assertThat(ExclusiveBoundaryNumbersInNumericFieldsFuzzer.extractExcludedBoundaries(oas30))
                .containsExactly(0L, 10L);

        Schema<?> oas31 = new NumberSchema()
                .exclusiveMinimumValue(new BigDecimal("1.5"))
                .exclusiveMaximumValue(new BigDecimal("9.5"));
        Assertions.assertThat(ExclusiveBoundaryNumbersInNumericFieldsFuzzer.extractExcludedBoundaries(oas31))
                .containsExactly(new BigDecimal("1.5"), new BigDecimal("9.5"));

        FuzzingData data = FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.POST)
                .payload("{\"count\":5,\"price\":5.0}")
                .requestPropertyTypes(Map.of("count", oas30, "price", oas31))
                .allFieldsByHttpMethod(Set.of("count", "price"))
                .requestContentTypes(List.of("application/json"))
                .headers(Set.of())
                .build();

        Mockito.when(serviceCaller.call(Mockito.any()))
                .thenReturn(CatsResponse.builder().responseCode(400).body("{}").build());
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);

        fuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(4))
                .reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.eq(true), Mockito.eq(true));
    }
}
