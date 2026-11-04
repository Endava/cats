package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class OneOfMultipleBranchesMergedFieldsFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private OneOfMultipleBranchesMergedFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        fuzzer = new OneOfMultipleBranchesMergedFieldsFuzzer(simpleExecutor);
    }

    @Test
    void shouldHaveDescriptionToStringAndSkippedMethods() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("OneOfMultipleBranchesMergedFieldsFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Test
    void shouldSkipWhenInvalidJson() {
        FuzzingData data = FuzzingData.builder().payload("not-json").build();
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldMergeDistinctPropertiesFromTwoOneOfBranches() {
        Schema<?> cardBranch = new ObjectSchema()
                .addProperty("paymentType", new StringSchema())
                .addProperty("cardNumber", new StringSchema());
        Schema<?> bankBranch = new ObjectSchema()
                .addProperty("paymentType", new StringSchema())
                .addProperty("iban", new StringSchema()._enum(List.of("RO49AAAA")))
                .addProperty("urgent", new BooleanSchema())
                .addProperty("routingCode", new IntegerSchema());
        Schema<?> paymentOneOf = new ObjectSchema()
                .discriminator(new Discriminator().propertyName("paymentType"))
                .oneOf(List.of(cardBranch, bankBranch));

        FuzzingData data = FuzzingData.builder()
                .path("/payments")
                .method(HttpMethod.POST)
                .payload("{\"paymentType\":\"CARD\",\"cardNumber\":\"4111\"}")
                .reqSchema(paymentOneOf)
                .schemaMap(Map.of())
                .allFieldsByHttpMethod(Set.of("paymentType", "cardNumber"))
                .requestPropertyTypes(Map.of())
                .build();

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor, Mockito.times(1)).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getPayload())
                .contains("\"cardNumber\":\"4111\"")
                .contains("\"iban\":\"RO49AAAA\"")
                .contains("\"urgent\":true")
                .contains("\"routingCode\":1");
    }
}
