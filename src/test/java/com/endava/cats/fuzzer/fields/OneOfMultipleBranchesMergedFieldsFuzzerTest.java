package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
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

    @Test
    void shouldMergeNestedReferencedBranchWithDefaultAndNumericSamples() {
        Schema<?> branch = new ObjectSchema().addProperty("extra", new StringSchema())
                .addProperty("amount", new NumberSchema());
        Schema<?> oneOf = new ObjectSchema().oneOf(List.of(new ObjectSchema(), new Schema<>().$ref("#/components/schemas/Branch")));
        FuzzingData data = FuzzingData.builder().method(HttpMethod.POST).path("/items")
                .payload("{\"nested\":{\"existing\":1}}")
                .reqSchema(new ObjectSchema().addProperty("nested", oneOf))
                .allFieldsByHttpMethod(Set.of("nested"))
                .requestPropertyTypes(Map.of("nested", oneOf)).schemaMap(Map.of("Branch", branch)).build();
        fuzzer.fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getPayload()).contains("\"extra\":\"catsOneOfMerged\"", "\"amount\":1");
    }

    @Test
    void shouldFindRootOneOfInOperationRequestBody() {
        Schema<?> oneOf = new ObjectSchema().oneOf(List.of(new ObjectSchema(), new ObjectSchema().addProperty("extra", new StringSchema())));
        var operation = new Operation().requestBody(new RequestBody()
                .content(new Content().addMediaType("application/json", new MediaType().schema(oneOf))));
        FuzzingData data = FuzzingData.builder().method(HttpMethod.POST).path("/items").payload("{\"id\":1}")
                .reqSchema(new ObjectSchema()).pathItem(new PathItem().post(operation))
                .schemaMap(Map.of()).requestPropertyTypes(Map.of()).build();
        fuzzer.fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getPayload()).contains("\"extra\":\"catsOneOfMerged\"");
    }

    @Test
    void shouldNotMergeWhenBranchesAddNoPropertiesOrTargetIsNotAnObject() {
        Schema<?> schema = new ObjectSchema().oneOf(List.of(new Schema<>(), new ObjectSchema().addProperty("kind", new StringSchema())))
                .discriminator(new Discriminator().propertyName("kind"));
        var target = new JsonPayloadTarget("$", true, schema);
        Assertions.assertThat(OneOfMultipleBranchesMergedFieldsFuzzer.mergeWithSecondBranch(
                JsonUtils.parseAsJsonElement("{}"), target, Map.of())).isEmpty();
        Assertions.assertThat(OneOfMultipleBranchesMergedFieldsFuzzer.mergeWithSecondBranch(
                JsonUtils.parseAsJsonElement("3"), target, Map.of())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"noMethod", "noOperation", "noBody", "noContent", "emptyContent"})
    void shouldSkipOperationsWithoutOneOfSchema(String missing) {
        var operation = new Operation();
        if (missing.equals("noContent")) operation.setRequestBody(new RequestBody());
        if (missing.equals("emptyContent")) operation.setRequestBody(new RequestBody().content(new Content()));
        FuzzingData data = FuzzingData.builder().payload("{}").method(missing.equals("noMethod") ? null : HttpMethod.POST)
                .pathItem(missing.equals("noOperation") ? new PathItem() : new PathItem().post(operation))
                .schemaMap(Map.of()).requestPropertyTypes(Map.of()).build();
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(simpleExecutor);
    }


    @Test
    void shouldSkipTargetWithoutSchema() {
        Assertions.assertThat(OneOfMultipleBranchesMergedFieldsFuzzer.mergeWithSecondBranch(
                JsonUtils.parseAsJsonElement("{}"), new JsonPayloadTarget("$", true, null), Map.of())).isEmpty();
    }

    @Test
    void shouldIgnoreUnresolvedBranchesAndUseFallbackForUnspecifiedPropertySchema() {
        Schema<?> schema = new ObjectSchema().oneOf(Arrays.asList(null,
                new ObjectSchema().addProperty("extra", null)));
        Assertions.assertThat(OneOfMultipleBranchesMergedFieldsFuzzer.mergeWithSecondBranch(
                JsonUtils.parseAsJsonElement("{}"), new JsonPayloadTarget("$", true, schema), Map.of()))
                .contains("{\"extra\":\"catsOneOfMerged\"}");
    }

    @Test
    void shouldSkipSingleBranchOneOf() {
        Schema<?> schema = new ObjectSchema().oneOf(List.of(new ObjectSchema()));
        fuzzer.fuzz(FuzzingData.builder().payload("{}").reqSchema(schema).schemaMap(Map.of()).build());
        Mockito.verifyNoInteractions(simpleExecutor);
    }

}
