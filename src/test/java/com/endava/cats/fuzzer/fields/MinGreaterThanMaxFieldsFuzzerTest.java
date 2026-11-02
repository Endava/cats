package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Set;

@QuarkusTest
class MinGreaterThanMaxFieldsFuzzerTest {
    private MinGreaterThanMaxFieldsFuzzer minGreaterThanMaxFieldsFuzzer;
    private SimpleExecutor executor;

    @BeforeEach
    void setup() {
        executor = Mockito.mock(SimpleExecutor.class);
        minGreaterThanMaxFieldsFuzzer = new MinGreaterThanMaxFieldsFuzzer(executor);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(minGreaterThanMaxFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(minGreaterThanMaxFieldsFuzzer).hasToString("MinGreaterThanMaxFieldsFuzzer");
    }

    @Test
    void shouldNotRunWhenNoMinMaxFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getReqSchema()).thenReturn(new Schema<String>());
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("someField"));
        minGreaterThanMaxFieldsFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @ParameterizedTest
    @CsvSource({"minPrice,someField", "maxPrice,someField", "minPrice,maxValue"})
    void shouldNotRunWhenOneBoundaryField(String field1, String field2) {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Schema<String> schema = new Schema<>();
        schema.setExample("Example");
        Mockito.when(data.getReqSchema()).thenReturn(schema);
        Mockito.when(data.getPayload()).thenReturn("""
                    {
                        "minPrice": 50,
                        "maxPrice": 100,
                        "maxValue": 200,
                        "someField": 0
                    }
                """);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of(field1, field2));
        minGreaterThanMaxFieldsFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldNotRunWhenNamingMatchesButNotNumbers() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Schema<String> schema = new Schema<>();
        schema.setExample("Example");
        Mockito.when(data.getReqSchema()).thenReturn(schema);
        Mockito.when(data.getPayload()).thenReturn("""
                    {
                        "minPrice": "50",
                        "maxPrice": 100,
                        "maxValue": "200",
                        "minValue": "100
                    }
                """);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("minPrice", "maxPrice", "minValue", "maxValue"));
        minGreaterThanMaxFieldsFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldRunWhenMinMaxFieldsAtStart() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Schema<String> schema = new Schema<>();
        schema.setExample("Example");
        Mockito.when(data.getReqSchema()).thenReturn(schema);
        Mockito.when(data.getPayload()).thenReturn("""
                    {
                        "minPrice": 50,
                        "maxPrice": 100
                    }
                """);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("minPrice", "maxPrice"));
        minGreaterThanMaxFieldsFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldRunWhenMinMaxFieldsAtEnd() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Schema<String> schema = new Schema<>();
        schema.setExample("Example");
        Mockito.when(data.getReqSchema()).thenReturn(schema);
        Mockito.when(data.getPayload()).thenReturn("""
                    {
                        "priceMin": 50,
                        "priceMax": 100
                    }
                """);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("priceMin", "priceMax"));
        minGreaterThanMaxFieldsFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldHandleEqualValuesAndFloatingPointAndNestedFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getReqSchema()).thenReturn(new Schema<String>());
        Mockito.when(data.getPayload()).thenReturn("""
                {
                    "range": {
                        "minInt": 10,
                        "maxInt": 10,
                        "minFloat": 5.5,
                        "maxFloat": 5.5
                    },
                    "other": {
                        "maxInt": 99
                    }
                }
                """);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of(
                "range#minInt", "range#maxInt", "range#minFloat", "range#maxFloat", "other#maxInt"));

        minGreaterThanMaxFieldsFuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> contexts = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor, Mockito.times(2)).execute(contexts.capture());
        Assertions.assertThat(contexts.getAllValues()).allSatisfy(ctx -> {
            Assertions.assertThat(ctx.getExpectedResponseCode()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
            Assertions.assertThat(JsonParser.parseString(ctx.getPayload()).getAsJsonObject().getAsJsonObject("other"))
                    .isEqualTo(JsonParser.parseString("{\"maxInt\":99}"));
        });
        for (String suffix : Set.of("Int", "Float")) {
            Assertions.assertThat(contexts.getAllValues())
                    .filteredOn(ctx -> ctx.getMutationTargets().contains(RequestTarget.body("range#min" + suffix)))
                    .singleElement().satisfies(ctx -> {
                        var range = JsonParser.parseString(ctx.getPayload()).getAsJsonObject().getAsJsonObject("range");
                        Assertions.assertThat(range.get("min" + suffix).getAsBigDecimal())
                                .isGreaterThan(range.get("max" + suffix).getAsBigDecimal());
                        Assertions.assertThat(ctx.getMutationTargets()).containsExactlyInAnyOrder(
                                RequestTarget.body("range#min" + suffix), RequestTarget.body("range#max" + suffix));
                        String untouched = suffix.equals("Int") ? "Float" : "Int";
                        var original = JsonParser.parseString(data.getPayload()).getAsJsonObject().getAsJsonObject("range");
                        Assertions.assertThat(range.get("min" + untouched)).isEqualTo(original.get("min" + untouched));
                        Assertions.assertThat(range.get("max" + untouched)).isEqualTo(original.get("max" + untouched));
                    });
        }
    }
}
