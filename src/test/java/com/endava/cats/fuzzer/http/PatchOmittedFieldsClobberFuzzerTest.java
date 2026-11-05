package com.endava.cats.fuzzer.http;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@QuarkusTest
class PatchOmittedFieldsClobberFuzzerTest {
    private RuntimeResourcePool pool;
    private FilterArguments filters;
    private ApiArguments arguments;
    private SimpleExecutor executor;
    private TestCaseListener listener;
    private PatchOmittedFieldsClobberFuzzer fuzzer;

    @BeforeEach
    void setup() {
        pool = Mockito.mock(RuntimeResourcePool.class);
        filters = Mockito.mock(FilterArguments.class);
        Mockito.when(filters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(true);
        arguments = new ApiArguments();
        arguments.setServer("https://api.example.com");
        executor = Mockito.mock(SimpleExecutor.class);
        listener = Mockito.mock(TestCaseListener.class);
        fuzzer = new PatchOmittedFieldsClobberFuzzer(pool, arguments, filters, executor, listener);
    }

    @Test
    void shouldWarnWhenPartialPatchWipesOmittedOptionalField() {
        FuzzingData data = patchData();
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items", HttpMethod.POST,
                        "https://api.example.com/items", "{}", 201, "{}", "https://api.example.com/items/1", null)));
        Mockito.when(pool.successfulExchanges("/items/{id}", HttpMethod.PATCH)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PATCH,
                        "https://api.example.com/items/1", "{\"name\":\"Updated\",\"description\":\"KeepMe\"}", 200, "{}", null, null)));

        List<SimpleExecutorContext> contexts = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            SimpleExecutorContext ctx = invocation.getArgument(0);
            contexts.add(ctx);
            if (contexts.size() == 1) {
                ctx.getResponseProcessor().accept(CatsResponse.builder()
                        .responseCode(200)
                        .body("{\"name\":\"Updated\",\"description\":\"KeepMe\"}")
                        .build(), data);
            } else if (contexts.size() == 2) {
                ctx.getResponseProcessor().accept(CatsResponse.builder()
                        .responseCode(200)
                        .body("{\"name\":\"Updated\",\"description\":null}")
                        .build(), data);
            } else if (contexts.size() == 3) {
                ctx.getResponseProcessor().accept(CatsResponse.builder()
                        .responseCode(200)
                        .body("{\"name\":\"Updated\",\"description\":null}")
                        .build(), data);
            }
            return null;
        }).when(executor).execute(Mockito.any());

        fuzzer.fuzz(data);

        Assertions.assertThat(contexts).hasSize(3);
        Assertions.assertThat(contexts.get(1).getHttpMethod()).isEqualTo(HttpMethod.PATCH);
        Assertions.assertThat(contexts.get(1).getPayload()).doesNotContain("description").contains("name");

        Mockito.verify(listener).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq(Reason.PATCH_CLOBBERED_OMITTED_FIELDS.value()),
                Mockito.anyString(),
                Mockito.eq("description"),
                Mockito.eq("KeepMe"));
    }

    @Test
    void shouldReportInfoWhenOmittedFieldIsPreserved() {
        FuzzingData data = patchData();
        Mockito.when(pool.successfulExchanges("/items", HttpMethod.POST)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items", HttpMethod.POST,
                        "https://api.example.com/items", "{}", 201, "{}", "https://api.example.com/items/1", null)));
        Mockito.when(pool.successfulExchanges("/items/{id}", HttpMethod.PATCH)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/items/{id}", HttpMethod.PATCH,
                        "https://api.example.com/items/1", "{\"name\":\"Updated\",\"description\":\"KeepMe\"}", 200, "{}", null, null)));

        Mockito.doAnswer(invocation -> {
            SimpleExecutorContext ctx = invocation.getArgument(0);
            ctx.getResponseProcessor().accept(CatsResponse.builder()
                    .responseCode(200)
                    .body("{\"name\":\"Updated\",\"description\":\"KeepMe\"}")
                    .build(), data);
            return null;
        }).when(executor).execute(Mockito.any());

        fuzzer.fuzz(data);

        Mockito.verify(listener, Mockito.never()).reportResultWarn(
                Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.<Object[]>any());
    }

    @Test
    void shouldSkipWhenNoOwnedPatchObserved() {
        fuzzer.fuzz(patchData());
        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldReturnMetadata() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("PatchOmittedFieldsClobberFuzzer");
    }

    private static FuzzingData patchData() {
        PathItem item = new PathItem().get(new Operation()).patch(new Operation());
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("name", new StringSchema());
        schema.addProperty("description", new StringSchema());
        schema.setRequired(List.of("name"));
        return FuzzingData.builder()
                .path("/items/{id}")
                .contractPath("/items/{id}")
                .method(HttpMethod.PATCH)
                .pathItem(item)
                .reqSchema(schema)
                .requestPropertyTypes(Map.of("name", new StringSchema(), "description", new StringSchema()))
                .allRequiredFields(List.of("name"))
                .build();
    }
}
