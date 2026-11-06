package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@QuarkusTest
class AllFieldsAtMaxBoundaryFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private FilesArguments filesArguments;
    private AllFieldsAtMaxBoundaryFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        Mockito.when(filesArguments.getRefData(Mockito.anyString())).thenReturn(Map.of());
        fuzzer = new AllFieldsAtMaxBoundaryFuzzer(simpleExecutor, filesArguments);
    }

    @Test
    void shouldHaveDescriptionToStringAndSkippedMethods() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("AllFieldsAtMaxBoundaryFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Test
    void shouldSkipWhenEmptyPayloadOrNoBoundedFields() {
        fuzzer.fuzz(FuzzingData.builder().payload("").build());
        fuzzer.fuzz(FuzzingData.builder()
                .path("/items")
                .payload("{\"name\":\"Alice\"}")
                .allFieldsByHttpMethod(Set.of("name"))
                .requestPropertyTypes(Map.of("name", new StringSchema()))
                .build());
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldCoMutateAllBoundedFieldsInSingleRequest() {
        Schema<?> boundedString = new StringSchema().maxLength(5);
        Schema<?> boundedInt = new IntegerSchema().maximum(BigDecimal.valueOf(100));
        Schema<?> exclusiveMaxInt = new IntegerSchema().maximum(BigDecimal.valueOf(50)).exclusiveMaximum(true);
        Schema<?> oas31ExclusiveMaxNum = new NumberSchema().exclusiveMaximumValue(new BigDecimal("10.5"));

        FuzzingData data = FuzzingData.builder()
                .path("/items")
                .method(HttpMethod.POST)
                .payload("{\"code\":\"ab\",\"qty\":1,\"rank\":2,\"score\":1.5}")
                .allFieldsByHttpMethod(Set.of("code", "qty", "rank", "score"))
                .requestPropertyTypes(Map.of(
                        "code", boundedString,
                        "qty", boundedInt,
                        "rank", exclusiveMaxInt,
                        "score", oas31ExclusiveMaxNum))
                .build();

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor, Mockito.times(1)).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getPayload())
                .contains("\"code\":\"aaaaa\"")
                .contains("\"qty\":100")
                .contains("\"rank\":49")
                .contains("\"score\":9.5");
    }

    @ParameterizedTest
    @MethodSource("unsupportedBoundaries")
    void shouldSkipUnsupportedBoundaries(Schema<?> schema) {
        Assertions.assertThat(AllFieldsAtMaxBoundaryFuzzer.computeMaxValidValue(schema)).isEmpty();
    }

    static Stream<Schema<?>> unsupportedBoundaries() {
        return Stream.of(null, new StringSchema()._enum(List.of("x")),
                new StringSchema()._const("x"), new IntegerSchema(), new BooleanSchema(),
                new StringSchema().format("uuid").maxLength(36), new StringSchema().maxLength(0),
                new StringSchema().maxLength(-1), new StringSchema());
    }

    @Test
    void shouldHandleNumericBoundariesAndCapStringLength() {
        Assertions.assertThat(AllFieldsAtMaxBoundaryFuzzer.computeMaxValidValue(new IntegerSchema().exclusiveMaximumValue(BigDecimal.TEN))).contains(9L);
        Assertions.assertThat(AllFieldsAtMaxBoundaryFuzzer.computeMaxValidValue(new NumberSchema().maximum(BigDecimal.TEN))).contains(BigDecimal.TEN);
        Assertions.assertThat(AllFieldsAtMaxBoundaryFuzzer.computeMaxValidValue(new StringSchema().maxLength(20000)))
                .contains("a".repeat(10000));
    }

    @Test
    void shouldGeneratePatternConstrainedBoundary() {
        Assertions.assertThat(AllFieldsAtMaxBoundaryFuzzer.computeMaxValidValue(new StringSchema().maxLength(5).pattern("[A-Z]{5}")))
                .hasValueSatisfying(value -> Assertions.assertThat(value.toString()).matches("[A-Z]{5}"));
    }

    @Test
    void shouldPreserveRefDataAndIgnoreAbsentFields() {
        Mockito.when(filesArguments.getRefData("/items")).thenReturn(Map.of("name", "fixed"));
        FuzzingData data = FuzzingData.builder().path("/items").payload("{\"name\":\"fixed\"}")
                .allFieldsByHttpMethod(Set.of("name", "missing"))
                .requestPropertyTypes(Map.of("name", new StringSchema().maxLength(10))).build();
        Assertions.assertThat(fuzzer.buildAllMaxPayload(data).mutatedFields()).isEmpty();
        Assertions.assertThat(new AllFieldsAtMaxBoundaryFuzzer(simpleExecutor, null)
                .buildAllMaxPayload(FuzzingData.builder().payload("{}").build()).mutatedFields()).isEmpty();
    }


    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"tooLong"})
    void shouldSkipUnproduciblePatternBoundary(String generated) {
        Schema<?> schema = new StringSchema().maxLength(5).pattern("[A-Z]{5}");
        try (var generator = Mockito.mockStatic(StringGenerator.class)) {
            generator.when(() -> StringGenerator.generateExactLength(schema, "[A-Z]{5}", 5)).thenReturn(generated);
            Assertions.assertThat(AllFieldsAtMaxBoundaryFuzzer.computeMaxValidValue(schema)).isEmpty();
        }
    }

    @Test
    void shouldSkipPatternGenerationFailure() {
        Schema<?> schema = new StringSchema().maxLength(5).pattern("[A-Z]{5}");
        try (var generator = Mockito.mockStatic(StringGenerator.class)) {
            generator.when(() -> StringGenerator.generateExactLength(schema, "[A-Z]{5}", 5))
                    .thenThrow(new IllegalArgumentException("Cannot generate pattern"));
            Assertions.assertThat(AllFieldsAtMaxBoundaryFuzzer.computeMaxValidValue(schema)).isEmpty();
        }
    }

}
