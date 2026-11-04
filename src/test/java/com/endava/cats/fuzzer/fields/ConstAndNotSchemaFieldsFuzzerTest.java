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
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
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
class ConstAndNotSchemaFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private ConstAndNotSchemaFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        FieldsIteratorExecutor executor = new FieldsIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        fuzzer = new ConstAndNotSchemaFieldsFuzzer(executor);
    }

    @Test
    void shouldHaveDescriptionAndToString() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("ConstAndNotSchemaFieldsFuzzer");
    }

    @Test
    void shouldSynthesizeViolatingValuesForConstAndNotSchemas() {
        Assertions.assertThat(ConstAndNotSchemaFieldsFuzzer.mutateConstValue(10)).isEqualTo(11L);
        Assertions.assertThat(ConstAndNotSchemaFieldsFuzzer.mutateConstValue(true)).isEqualTo(false);
        Assertions.assertThat(ConstAndNotSchemaFieldsFuzzer.mutateConstValue("FIXED")).isEqualTo("FIXED_violatedConst");

        Assertions.assertThat(ConstAndNotSchemaFieldsFuzzer.synthesizeValueMatchingNotSchema(null)).isNull();
        Assertions.assertThat(ConstAndNotSchemaFieldsFuzzer.synthesizeValueMatchingNotSchema(new StringSchema()._const("ADMIN"))).isEqualTo("ADMIN");
        Assertions.assertThat(ConstAndNotSchemaFieldsFuzzer.synthesizeValueMatchingNotSchema(new StringSchema()._enum(List.of("ROOT")))).isEqualTo("ROOT");
        Assertions.assertThat(ConstAndNotSchemaFieldsFuzzer.synthesizeValueMatchingNotSchema(new StringSchema()._default("DEF"))).isEqualTo("DEF");
        Assertions.assertThat(ConstAndNotSchemaFieldsFuzzer.synthesizeValueMatchingNotSchema(new StringSchema().example("EX"))).isEqualTo("EX");
        Assertions.assertThat(ConstAndNotSchemaFieldsFuzzer.synthesizeValueMatchingNotSchema(new IntegerSchema().minimum(BigDecimal.valueOf(5)))).isEqualTo(5L);
        Assertions.assertThat(ConstAndNotSchemaFieldsFuzzer.synthesizeValueMatchingNotSchema(new BooleanSchema())).isEqualTo(true);
        Assertions.assertThat(ConstAndNotSchemaFieldsFuzzer.synthesizeValueMatchingNotSchema(new IntegerSchema())).isEqualTo(0);
        Assertions.assertThat(ConstAndNotSchemaFieldsFuzzer.synthesizeValueMatchingNotSchema(new StringSchema().minLength(3))).isEqualTo("aaa");
    }

    @Test
    void shouldExecuteFuzzingForConstAndNotFields() {
        Schema<?> constField = new StringSchema()._const("V1");
        Schema<?> notField = new StringSchema().not(new StringSchema()._enum(List.of("FORBIDDEN")));

        FuzzingData data = FuzzingData.builder()
                .path("/items")
                .method(HttpMethod.POST)
                .payload("{\"version\":\"V1\",\"tag\":\"ALLOWED\"}")
                .requestPropertyTypes(Map.of("version", constField, "tag", notField))
                .allFieldsByHttpMethod(Set.of("version", "tag"))
                .schemaMap(Map.of())
                .requestContentTypes(List.of("application/json"))
                .headers(Set.of())
                .build();

        Mockito.when(serviceCaller.call(Mockito.any()))
                .thenReturn(CatsResponse.builder().responseCode(400).body("{}").build());
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);

        fuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(2))
                .reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.eq(true), Mockito.eq(true));
    }
}
