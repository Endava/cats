package com.endava.cats.fuzzer.http;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class SecondPhaseConsistencyFuzzersTest {
    private RuntimeResourcePool pool;
    private FilterArguments filters;
    private ApiArguments arguments;
    private SimpleExecutor executor;
    private TestCaseListener listener;

    @BeforeEach
    void setup() {
        pool = Mockito.mock(RuntimeResourcePool.class);
        filters = Mockito.mock(FilterArguments.class);
        Mockito.when(filters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(true);
        arguments = new ApiArguments();
        arguments.setServer("https://api.example.com");
        executor = Mockito.mock(SimpleExecutor.class);
        listener = Mockito.mock(TestCaseListener.class);
    }

    @Test
    void conditionalGetSendsObservedTagOnlyToOriginalSameOriginPath() {
        FuzzingData data = getData();
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.GET)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange(data.getPath(), HttpMethod.GET,
                        "https://api.example.com/items/1", "{}", 200, "{}", null, "\"abc\"")));
        new ConditionalEtagFuzzer(pool, arguments, executor, listener).fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getPath()).isEqualTo("/items/1");
        Assertions.assertThat(captor.getValue().isReplaceRefData()).isFalse();
        Assertions.assertThat(captor.getValue().getHeaders()).anySatisfy(header -> {
            Assertions.assertThat(header.getName()).isEqualTo("If-None-Match");
            Assertions.assertThat(header.getValue()).isEqualTo("\"abc\"");
        });
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(200)
                .headers(List.of(new KeyValuePair<>("ETag", "\"abc\""))).build(), data);
        Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.eq(data), Mockito.eq("Conditional GET ignored"), Mockito.anyString());
    }

    @Test
    void staleIfMatchOnlyReplaysWritesToOwnedResourcesWithTwoDistinctObservedTags() {
        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.GET)).thenReturn(List.of(
                get("\"old\""), get("\"new\"")));
        new ConditionalEtagFuzzer(pool, arguments, executor, listener).fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getMutationTargets()).hasSize(1);
        Assertions.assertThat(captor.getValue().getHeaders()).anySatisfy(header -> {
            Assertions.assertThat(header.getName()).isEqualTo("If-Match");
            Assertions.assertThat(header.getValue()).isEqualTo("\"old\"");
        });
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(412).build(), data);
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.<Object[]>any());
    }

    @Test
    void staleIfMatchUsesPreUpdateGetTagWhenWriteExchangeReturnedUpdatedTag() {
        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.GET)).thenReturn(List.of(get("\"v1\"")));
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PUT,
                        "https://api.example.com/items/1", "{\"name\":\"same\"}", 200, "{}", null, "\"v2\"")));
        new ConditionalEtagFuzzer(pool, arguments, executor, listener).fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getHeaders()).anySatisfy(header -> {
            Assertions.assertThat(header.getName()).isEqualTo("If-Match");
            Assertions.assertThat(header.getValue()).isEqualTo("\"v1\"");
        });
    }

    @Test
    void conditionalGetAndStaleIfMatchMatchObservedRequestUrlsThatIncludeQueryParameters() {
        FuzzingData getData = getData();
        Mockito.when(pool.successfulExchanges(getData.getPath(), HttpMethod.GET)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange(getData.getPath(), HttpMethod.GET,
                        "https://api.example.com/items/1?expand=true", "{}", 200, "{}", null, "\"v1\"")));
        new ConditionalEtagFuzzer(pool, arguments, executor, listener).fuzz(getData);

        FuzzingData putData = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        Mockito.when(pool.successfulExchanges(putData.getPath(), HttpMethod.PUT)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PUT,
                        "https://api.example.com/items/1", "{\"name\":\"same\"}", 200, "{}", null, "\"v2\"")));
        new ConditionalEtagFuzzer(pool, arguments, executor, listener).fuzz(putData);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor, Mockito.times(2)).execute(captor.capture());
        Assertions.assertThat(captor.getAllValues().get(0).getPath()).isEqualTo("/items/1");
        Assertions.assertThat(captor.getAllValues().get(1).getPath()).isEqualTo("/items/1");
    }

    @Test
    void staleIfMatchSkipsWhenNoOwnedItemExists() {
        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.GET)).thenReturn(List.of(
                get("\"old\""), get("\"new\"")));
        new ConditionalEtagFuzzer(pool, arguments, executor, listener).fuzz(data);
        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void readBackSkipsUnownedResourcesAndDoesNotSendAnotherUpdate() {
        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));
        UpdateReadConsistencyFuzzer fuzzer = new UpdateReadConsistencyFuzzer(pool, arguments, filters, executor, listener);
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(executor);
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        fuzzer.fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getHttpMethod()).isEqualTo(HttpMethod.GET);
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(200)
                .body("{\"name\":\"changed\",\"secret\":\"value\"}").build(), data);
        Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.eq(data), Mockito.eq("Possible update/read inconsistency"),
                Mockito.anyString(), Mockito.<Object[]>any());
    }

    @Test
    void readBackAndPutIdempotencyAcceptResourcesOwnedViaPostResponseBodyIdWithoutLocationHeader() {
        FuzzingData data = writeData(HttpMethod.PUT);
        RuntimeResourcePool.SuccessfulExchange postWithoutLocation = new RuntimeResourcePool.SuccessfulExchange(
                "/items", HttpMethod.POST, "https://api.example.com/items", "{}", 200, "{\"id\":\"1\",\"name\":\"created\"}", null, null);
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(postWithoutLocation));
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));

        UpdateReadConsistencyFuzzer updateReadFuzzer = new UpdateReadConsistencyFuzzer(pool, arguments, filters, executor, listener);
        updateReadFuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getPath()).isEqualTo("/items/1");
        Assertions.assertThat(captor.getValue().getHttpMethod()).isEqualTo(HttpMethod.GET);
    }

    @Test
    void readBackComparesAllOfPropertiesAndTreatsNumericallyEqualValuesAsConsistent() {
        Schema<?> baseItem = new ObjectSchema().addProperty("name", new StringSchema())
                .addProperty("scheduledAt", new StringSchema());
        Schema<?> composed = new ComposedSchema()
                .addAllOfItem(new Schema<>().$ref("#/components/schemas/BaseItem"))
                .addAllOfItem(new ObjectSchema().addProperty("price", new NumberSchema()));
        FuzzingData data = FuzzingData.builder().method(HttpMethod.PUT).path("/items/{id}").contractPath("/items/{id}")
                .pathItem(new PathItem().get(new Operation()).put(new Operation())).headers(Set.of())
                .reqSchema(composed).schemaMap(Map.of("BaseItem", baseItem))
                .payload("{\"name\":\"Same \",\"price\":10,\"scheduledAt\":\"2026-01-01T00:00:00Z\"}").build();
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PUT,
                        "https://api.example.com/items/1",
                        "{\"name\":\"Same \",\"price\":10,\"scheduledAt\":\"2026-01-01T00:00:00Z\"}", 200, "{}", null, null)));

        new UpdateReadConsistencyFuzzer(pool, arguments, filters, executor, listener).fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(200)
                .body("{\"name\":\"same\",\"price\":10.00,\"scheduledAt\":\"2026-01-01T00:00:00+00:00\"}").build(), data);
        Mockito.verify(listener, Mockito.never()).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.anyString(), Mockito.<Object[]>any());
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.eq(data),
                Mockito.eq("Compared {} fields from a successful update with a later read"), Mockito.eq(3));
    }

    @Test
    void putIdempotencyRunsTwoIdenticalWritesAndReadsOnlyOnOwnedResource() {
        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));
        PutIdempotencyFuzzer fuzzer = new PutIdempotencyFuzzer(pool, arguments, filters, executor, listener);
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(executor);
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        List<SimpleExecutorContext> sent = new ArrayList<>();
        Mockito.doAnswer(call -> {
            SimpleExecutorContext step = call.getArgument(0);
            sent.add(step);
            CatsResponse response = step.getHttpMethod() == HttpMethod.PUT
                    ? CatsResponse.builder().responseCode(200).body("{}").build()
                    : CatsResponse.builder().responseCode(200).body("{\"name\":\"same\",\"version\":1}").build();
            step.getResponseProcessor().accept(response, data);
            return null;
        }).when(executor).execute(Mockito.any());
        fuzzer.fuzz(data);
        Assertions.assertThat(sent).extracting(SimpleExecutorContext::getHttpMethod)
                .containsExactly(HttpMethod.PUT, HttpMethod.GET, HttpMethod.PUT, HttpMethod.GET);
        Assertions.assertThat(sent.get(0).getPayload()).isEqualTo(sent.get(2).getPayload());
        Assertions.assertThat(sent.get(0).getPath()).isEqualTo("/items/1");
        Assertions.assertThat(sent.get(0).isReplaceRefData()).isFalse();
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.eq(data),
                Mockito.eq("Resource state did not change after repeating the identical PUT"));
    }

    @Test
    void putIdempotencyIgnoresSnakeCaseAndNestedVolatileFieldsAcrossRepeatedPuts() {
        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        int[] reads = {0};
        Mockito.doAnswer(call -> {
            SimpleExecutorContext step = call.getArgument(0);
            int readIndex = ++reads[0];
            CatsResponse response = step.getHttpMethod() == HttpMethod.PUT
                    ? CatsResponse.builder().responseCode(200).body("{}").build()
                    : CatsResponse.builder().responseCode(200).body(
                            "{\"name\":\"same\",\"updated_at\":\"2026-10-16T00:00:0" + readIndex
                                    + "Z\",\"meta\":{\"last_modified\":\"" + readIndex + "\",\"version\":" + readIndex + "}}")
                            .build();
            step.getResponseProcessor().accept(response, data);
            return null;
        }).when(executor).execute(Mockito.any());

        new PutIdempotencyFuzzer(pool, arguments, filters, executor, listener).fuzz(data);

        Mockito.verify(listener, Mockito.never()).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString());
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.eq(data),
                Mockito.eq("Resource state did not change after repeating the identical PUT"));
        for (String volatileField : List.of("lastModifiedDate", "updatedDate", "modifiedTime", "createdDate", "updated_at", "etag")) {
            Assertions.assertThat(PutIdempotencyFuzzer.isVolatileField(volatileField)).as(volatileField).isTrue();
        }
        for (String stableField : List.of("candidateId", "updateId", "birthDate", "name")) {
            Assertions.assertThat(PutIdempotencyFuzzer.isVolatileField(stableField)).as(stableField).isFalse();
        }
    }

    @Test
    void putIdempotencyWarnsWhenSecondPutChangesState() {
        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        int[] reads = {0};
        Mockito.doAnswer(call -> {
            SimpleExecutorContext step = call.getArgument(0);
            CatsResponse response = step.getHttpMethod() == HttpMethod.PUT
                    ? CatsResponse.builder().responseCode(200).body("{}").build()
                    : CatsResponse.builder().responseCode(200).body("{\"name\":\"" + (++reads[0]) + "\"}").build();
            step.getResponseProcessor().accept(response, data);
            return null;
        }).when(executor).execute(Mockito.any());
        new PutIdempotencyFuzzer(pool, arguments, filters, executor, listener).fuzz(data);
        Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.eq(data),
                Mockito.eq("PUT is not idempotent"), Mockito.anyString());
    }

    @Test
    void coversConditionalEtagBranchesAndResponseProcessors() {
        ConditionalEtagFuzzer fuzzer = new ConditionalEtagFuzzer(pool, arguments, executor, listener);
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("ConditionalEtagFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.POST, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);

        fuzzer.fuzz(FuzzingData.builder().method(HttpMethod.POST).path("/items").build());

        FuzzingData getData = FuzzingData.builder().method(HttpMethod.GET).path("/items/{id}").contractPath("/items/{id}")
                .headers(Set.of(com.endava.cats.model.CatsHeader.from("If-None-Match", "\"old\"", false, null)))
                .payload("{}").build();
        Mockito.when(pool.successfulExchanges(getData.getPath(), HttpMethod.GET)).thenReturn(List.of(
                get(null), get(""), get("\"bad\r\n\"")));
        fuzzer.fuzz(getData);
        Mockito.verifyNoInteractions(executor);

        Mockito.when(pool.successfulExchanges(getData.getPath(), HttpMethod.GET)).thenReturn(List.of(get("\"v1\"")));
        fuzzer.fuzz(getData);
        ArgumentCaptor<SimpleExecutorContext> getCaptor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(getCaptor.capture());
        getCaptor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(304).build(), getData);
        getCaptor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(500).build(), getData);
        getCaptor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(200)
                .headers(List.of(new KeyValuePair<>("ETag", "\"v2\""))).build(), getData);
        getCaptor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(400).build(), getData);

        FuzzingData patchWithoutGet = FuzzingData.builder().method(HttpMethod.PATCH).path("/items/{id}")
                .headers(Set.of(com.endava.cats.model.CatsHeader.from("If-Match", "\"v1\"", false, null))).build();
        fuzzer.fuzz(patchWithoutGet);

        FuzzingData patchData = writeData(HttpMethod.PATCH);
        Mockito.when(pool.successfulExchanges(patchData.getPath(), HttpMethod.GET)).thenReturn(List.of(
                get("\"v1\""),
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.GET, null, "{}", 200, "{}", null, "\"v1\""),
                get(null)));
        Mockito.when(pool.successfulExchanges(patchData.getPath(), HttpMethod.PATCH)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PATCH,
                        "https://api.example.com/items/1", "{\"name\":\"same\"}", 200, "{}", null, null),
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PATCH,
                        "https://api.example.com/items/1", "{\"name\":\"same\"}", 200, "{}", null, "\"v1\"")));
        fuzzer.fuzz(patchData);

        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        Mockito.when(pool.successfulExchanges(patchData.getPath(), HttpMethod.GET)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.GET,
                        "https://api.example.com/items/1?a=1", "{}", 200, "{}", null, "\"v1\""),
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.GET,
                        "https://api.example.com/items/1?b=2", "{}", 200, "{}", null, "\"v2\"")));
        Mockito.when(pool.successfulExchanges(patchData.getPath(), HttpMethod.PATCH)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PATCH,
                        "https://api.example.com/items/1", "{\"name\":\"same\"}", 200, "{}", null, "\"v2\"")));
        fuzzer.fuzz(patchData);
        ArgumentCaptor<SimpleExecutorContext> patchCaptor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor, Mockito.times(2)).execute(patchCaptor.capture());
        SimpleExecutorContext staleCtx = patchCaptor.getAllValues().getLast();
        staleCtx.getResponseProcessor().accept(CatsResponse.builder().responseCode(409).build(), patchData);
        staleCtx.getResponseProcessor().accept(CatsResponse.builder().responseCode(200).build(), patchData);
        staleCtx.getResponseProcessor().accept(CatsResponse.builder().responseCode(500).build(), patchData);
        staleCtx.getResponseProcessor().accept(CatsResponse.builder().responseCode(400).build(), patchData);
    }

    @Test
    void coversPutIdempotencyAndUpdateReadConsistencyBranches() {
        PutIdempotencyFuzzer putFuzzer = new PutIdempotencyFuzzer(pool, arguments, filters, executor, listener);
        Assertions.assertThat(putFuzzer.description()).isNotBlank();
        Assertions.assertThat(putFuzzer).hasToString("PutIdempotencyFuzzer");
        Assertions.assertThat(putFuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);

        putFuzzer.fuzz(FuzzingData.builder().method(HttpMethod.GET).path("/items/{id}").build());
        FuzzingData putWithIfMatch = FuzzingData.builder().method(HttpMethod.PUT).path("/items/{id}")
                .pathItem(new PathItem().get(new Operation()).put(new Operation()))
                .headers(Set.of(com.endava.cats.model.CatsHeader.from("If-Match", "\"1\"", false, null))).build();
        putFuzzer.fuzz(putWithIfMatch);

        FuzzingData data = writeData(HttpMethod.PUT);
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(post()));
        Mockito.when(pool.successfulExchanges(data.getPath(), HttpMethod.PUT)).thenReturn(List.of(put()));

        Mockito.doAnswer(call -> {
            SimpleExecutorContext step = call.getArgument(0);
            step.getResponseProcessor().accept(CatsResponse.builder().responseCode(500).build(), data);
            step.getResponseProcessor().accept(CatsResponse.builder().responseCode(400).build(), data);
            return null;
        }).when(executor).execute(Mockito.any());
        putFuzzer.fuzz(data);

        Mockito.doAnswer(call -> {
            SimpleExecutorContext step = call.getArgument(0);
            if (step.getHttpMethod() == HttpMethod.PUT) {
                step.getResponseProcessor().accept(CatsResponse.builder().responseCode(200).body("{}").build(), data);
            } else {
                step.getResponseProcessor().accept(CatsResponse.builder().responseCode(500).build(), data);
                step.getResponseProcessor().accept(CatsResponse.builder().responseCode(404).build(), data);
                step.getResponseProcessor().accept(CatsResponse.builder().responseCode(200).body("[1,2]").build(), data);
            }
            return null;
        }).when(executor).execute(Mockito.any());
        putFuzzer.fuzz(data);

        int[] putAttempt = {0};
        Mockito.doAnswer(call -> {
            SimpleExecutorContext step = call.getArgument(0);
            if (step.getHttpMethod() == HttpMethod.PUT) {
                int code = (++putAttempt[0] == 1) ? 200 : 400;
                step.getResponseProcessor().accept(CatsResponse.builder().responseCode(code).body("{}").build(), data);
            } else {
                step.getResponseProcessor().accept(CatsResponse.builder().responseCode(200)
                        .body("{\"items\":[null,{\"updatedAt\":\"now\",\"name\":\"ok\"}]}").build(), data);
            }
            return null;
        }).when(executor).execute(Mockito.any());
        putFuzzer.fuzz(data);

        UpdateReadConsistencyFuzzer updateFuzzer = new UpdateReadConsistencyFuzzer(pool, arguments, filters, executor, listener);
        Assertions.assertThat(updateFuzzer.description()).isNotBlank();
        Assertions.assertThat(updateFuzzer).hasToString("UpdateReadConsistencyFuzzer");
        Assertions.assertThat(updateFuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);

        updateFuzzer.fuzz(FuzzingData.builder().method(HttpMethod.POST).path("/items").build());

        Schema<?> oneOfSchema = new ComposedSchema()
                .addOneOfItem(new Schema<>().$ref("#/components/schemas/MissingSchema"))
                .addOneOfItem(new ObjectSchema().addProperty("readonlyField", new StringSchema().readOnly(true)))
                .addAnyOfItem(new ObjectSchema().addProperty("count", new NumberSchema()));
        FuzzingData patchData = FuzzingData.builder().method(HttpMethod.PATCH).path("/items/{id}").contractPath("/items/{id}")
                .pathItem(new PathItem().get(new Operation()).patch(new Operation())).headers(Set.of())
                .reqSchema(oneOfSchema)
                .requestPropertyTypes(Map.of("", new StringSchema(), "extra", new StringSchema(), "nested#field", new StringSchema()))
                .payload("{\"count\":5,\"extra\":\"abc\"}").build();
        Mockito.when(pool.successfulExchanges(patchData.getPath(), HttpMethod.PATCH)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PATCH,
                        "https://api.example.com/items/1", "{\"count\":5,\"extra\":\"abc\"}", 200, "{}", null, null)));

        Mockito.doAnswer(call -> {
            SimpleExecutorContext step = call.getArgument(0);
            step.getResponseProcessor().accept(CatsResponse.builder().responseCode(404).build(), patchData);
            step.getResponseProcessor().accept(CatsResponse.builder().responseCode(410).build(), patchData);
            step.getResponseProcessor().accept(CatsResponse.builder().responseCode(500).build(), patchData);
            step.getResponseProcessor().accept(CatsResponse.builder().responseCode(200).body("[1,2]").build(), patchData);
            step.getResponseProcessor().accept(CatsResponse.builder().responseCode(200).body("{\"unmatched\":true}").build(), patchData);
            step.getResponseProcessor().accept(CatsResponse.builder().responseCode(200).body("{\"count\":9,\"extra\":123}").build(), patchData);
            return null;
        }).when(executor).execute(Mockito.any());
        updateFuzzer.fuzz(patchData);
    }

    private FuzzingData getData() {
        return FuzzingData.builder().method(HttpMethod.GET).path("/items/{id}").contractPath("/items/{id}")
                .headers(Set.of()).payload("{}").build();
    }

    private FuzzingData writeData(HttpMethod method) {
        return FuzzingData.builder().method(method).path("/items/{id}").contractPath("/items/{id}")
                .pathItem(new PathItem().get(new Operation()).put(new Operation())).headers(Set.of())
                .reqSchema(new ObjectSchema().addProperty("name", new StringSchema())
                        .addProperty("secret", new StringSchema().writeOnly(true)))
                .payload("{\"name\":\"same\"}").build();
    }

    private RuntimeResourcePool.SuccessfulExchange put() {
        return new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PUT,
                "https://api.example.com/items/1", "{\"name\":\"same\"}", 200, "{}", null, null);
    }

    private RuntimeResourcePool.SuccessfulExchange get(String etag) {
        return new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.GET,
                "https://api.example.com/items/1", "{}", 200, "{}", null, etag);
    }

    private RuntimeResourcePool.SuccessfulExchange post() {
        return new RuntimeResourcePool.SuccessfulExchange("/items", HttpMethod.POST,
                "https://api.example.com/items", "{}", 201, "{}", "/items/1", null);
    }
}
