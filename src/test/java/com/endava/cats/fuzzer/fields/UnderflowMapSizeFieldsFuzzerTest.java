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
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class UnderflowMapSizeFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private UnderflowMapSizeFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        FieldsIteratorExecutor executor = new FieldsIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        fuzzer = new UnderflowMapSizeFieldsFuzzer(executor);
    }

    @Test
    void shouldHaveDescriptionAndToString() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("UnderflowMapSizeFieldsFuzzer");
    }

    @Test
    void shouldBuildUnderflowMapForMinProperties() {
        Schema<?> minOne = new MapSchema().minProperties(1);
        Schema<?> minTwo = new MapSchema().minProperties(2);
        FuzzingData data = FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.POST)
                .payload("{\"labels\":{\"k1\":\"v1\"},\"attributes\":{\"a\":\"1\",\"b\":\"2\"}}")
                .requestPropertyTypes(Map.of("labels", minOne, "attributes", minTwo))
                .allFieldsByHttpMethod(Set.of("labels", "attributes"))
                .requestContentTypes(List.of("application/json"))
                .headers(Set.of())
                .build();

        Assertions.assertThat(UnderflowMapSizeFieldsFuzzer.buildUnderflowMap(data, minOne, "labels")).isEqualTo("{}");
        Assertions.assertThat(UnderflowMapSizeFieldsFuzzer.buildUnderflowMap(data, minTwo, "attributes"))
                .contains("\"a\"")
                .doesNotContain("\"b\"");

        Mockito.when(serviceCaller.call(Mockito.any()))
                .thenReturn(CatsResponse.builder().responseCode(400).body("{}").build());
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);

        fuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(2))
                .reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.eq(true), Mockito.eq(true));
    }
}
