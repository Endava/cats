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
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

@QuarkusTest
class CreatedResourceLocationFuzzerTest {
    private SimpleExecutor executor;
    private RuntimeResourcePool pool;
    private TestCaseListener listener;
    private CreatedResourceLocationFuzzer fuzzer;
    private FuzzingData data;

    @BeforeEach
    void setup() {
        executor = Mockito.mock(SimpleExecutor.class);
        pool = Mockito.mock(RuntimeResourcePool.class);
        listener = Mockito.mock(TestCaseListener.class);
        ApiArguments args = new ApiArguments();
        args.setServer("https://api.example.com/v1");
        FilterArguments filters = Mockito.mock(FilterArguments.class);
        Mockito.when(filters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(true);
        fuzzer = new CreatedResourceLocationFuzzer(executor, pool, args, filters, listener);
        OpenAPI spec = new OpenAPI().paths(new Paths()
                .addPathItem("/users", new PathItem().post(new Operation()))
                .addPathItem("/users/{id}", new PathItem().get(new Operation())));
        Mockito.when(filters.getPathsToRun(spec)).thenReturn(List.of("/users", "/users/{id}"));
        data = FuzzingData.builder().method(HttpMethod.POST).path("/users")
                .contractPath("/users").openApi(spec).payload("{}").build();
    }

    @Test
    void followsDocumentedSameOriginLocationOnce() {
        Mockito.when(pool.successfulExchanges("/users", HttpMethod.POST)).thenReturn(List.of(
                exchange("https://api.example.com/v1/users", "users/1"),
                exchange("https://api.example.com/v1/users", "/v1/users/2")));
        fuzzer.fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        Assertions.assertThat(captor.getValue().getPath()).isEqualTo("/users/1");
        Assertions.assertThat(captor.getValue().getHttpMethod()).isEqualTo(HttpMethod.GET);
        Assertions.assertThat(captor.getValue().isReplaceUrlParams()).isFalse();
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(404).build(), data);
        Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.eq(data), Mockito.eq("Created resource unavailable"),
                Mockito.anyString(), Mockito.<Object[]>any());
    }

    @Test
    void skipsExternalUndocumentedAndUnsuccessfulLocations() {
        Mockito.when(pool.successfulExchanges("/users", HttpMethod.POST)).thenReturn(List.of(
                exchange("https://api.example.com/v1/users", "https://evil.example/v1/users/1"),
                exchange("https://api.example.com/v1/users", "/v1/other/1"),
                exchange("https://api.example.com/v1/users", "//api.example.com.evil/v1/users/1"),
                new RuntimeResourcePool.SuccessfulExchange("/users", HttpMethod.POST,
                        "https://api.example.com/v1/users", "{}", 202, null, "/v1/users/1", null)));
        fuzzer.fuzz(data);
        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void validatesHostPortPrefixAndTemplate() {
        ApiArguments args = new ApiArguments();
        args.setServer("https://api.example.com/v1");
        Assertions.assertThat(ObservedHttpTarget.relativePath(args, "https://api.example.com/v1/users/2"))
                .contains("/users/2");
        for (String unsafe : List.of("https://api.example.com.evil/v1/users/2", "https://api.example.com:444/v1/users/2",
                "http://api.example.com/v1/users/2", "https://api.example.com/v11/users/2", "https://api.example.com/v1/users%2F2",
                "https://api.example.com/v1/users/2?token=x")) {
            Assertions.assertThat(ObservedHttpTarget.relativePath(args, unsafe)).isEmpty();
        }
        Assertions.assertThat(ObservedHttpTarget.matchesTemplate("/users/{id}", "/users/2")).isTrue();
        Assertions.assertThat(ObservedHttpTarget.matchesTemplate("/users/{id}", "/users/2/nested")).isFalse();
        Assertions.assertThat(ObservedHttpTarget.matchesTemplate("/users/{id}", "/users/")).isFalse();
    }

    @Test
    void coversCreatedResourceLocationAndObservedHttpTargetEdgeCases() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("CreatedResourceLocationFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);

        fuzzer.fuzz(FuzzingData.builder().method(HttpMethod.GET).path("/users").build());
        fuzzer.fuzz(FuzzingData.builder().method(HttpMethod.POST).path("/users").openApi(null).build());

        FilterArguments noGetFilters = Mockito.mock(FilterArguments.class);
        Mockito.when(noGetFilters.isHttpMethodSupplied(HttpMethod.GET)).thenReturn(false);
        ApiArguments args = new ApiArguments();
        args.setServer("http://api.example.com/v1/");
        new CreatedResourceLocationFuzzer(executor, pool, args, noGetFilters, listener).fuzz(data);
        Mockito.verifyNoInteractions(executor);

        Mockito.when(pool.successfulExchanges("/users", HttpMethod.POST)).thenReturn(List.of(
                exchange("https://api.example.com/v1/users", "/v1/users/1")));
        fuzzer.fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());

        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(410).build(), data);
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(200).build(), data);
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(500).build(), data);
        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.<Object[]>any());
        Mockito.verify(listener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.any(), Mockito.eq(false));

        ApiArguments blankArgs = new ApiArguments();
        Assertions.assertThat(ObservedHttpTarget.relativePath(blankArgs, "http://api.example.com/v1/users/1")).isEmpty();
        Assertions.assertThat(ObservedHttpTarget.relativePath(args, "")).isEmpty();
        Assertions.assertThat(ObservedHttpTarget.relativePath(args, "http://[::1")).isEmpty();
        Assertions.assertThat(ObservedHttpTarget.relativePath(args, "ftp://api.example.com/v1/users/1")).isEmpty();
        Assertions.assertThat(ObservedHttpTarget.relativePath(args, "http://user@api.example.com/v1/users/1")).isEmpty();
        Assertions.assertThat(ObservedHttpTarget.relativePath(args, "http://api.example.com/v1/users/1#frag")).isEmpty();
        Assertions.assertThat(ObservedHttpTarget.relativePath(args, "http://api.example.com/v1/users%5c1")).isEmpty();
        Assertions.assertThat(ObservedHttpTarget.relativePath(args, "http://api.example.com/v1/users%2e1")).isEmpty();
        Assertions.assertThat(ObservedHttpTarget.relativePath(args, "http://api.example.com:80/v1/users/1")).contains("/users/1");
        Assertions.assertThat(ObservedHttpTarget.relativeRequestPath(args, null)).isEmpty();
        Assertions.assertThat(ObservedHttpTarget.resolvedLocation("http://api.example.com/v1/users", "://bad")).isEmpty();
        Assertions.assertThat(ObservedHttpTarget.ownedPath(pool, args, "users", "http://api.example.com/v1/users/1")).isEmpty();
        Assertions.assertThat(ObservedHttpTarget.ownedPath(pool, args, "/users/{id}/sub", "http://api.example.com/v1/users/1")).isEmpty();
        Assertions.assertThat(ObservedHttpTarget.ownedPath(pool, args, "/users/{id}", "http://evil.example.com/v1/users/1")).isEmpty();

        Mockito.when(pool.successfulExchanges("/categories", HttpMethod.POST)).thenReturn(List.of(
                new RuntimeResourcePool.SuccessfulExchange("/categories", HttpMethod.POST,
                        "http://evil.example.com/v1/categories/", "{}", 200, "{\"categoryId\":\"cat-1\"}", null, null),
                new RuntimeResourcePool.SuccessfulExchange("/categories", HttpMethod.POST,
                        "http://api.example.com/v1/categories/", "{}", 201, "{\"categoryId\":\"cat-2\"}", "http://evil.example.com/v1/categories/cat-2", null),
                new RuntimeResourcePool.SuccessfulExchange("/categories", HttpMethod.POST,
                        "http://api.example.com/v1/categories/", "{}", 200, "not-json", null, null),
                new RuntimeResourcePool.SuccessfulExchange("/categories", HttpMethod.POST,
                        "http://api.example.com/v1/categories/", "{}", 200, "[1,2]", null, null),
                new RuntimeResourcePool.SuccessfulExchange("/categories", HttpMethod.POST,
                        "http://api.example.com/v1/categories/", "{}", 200, "{\"categoryId\":\"a/b\",\"other\":\"x\"}", null, null),
                new RuntimeResourcePool.SuccessfulExchange("/categories", HttpMethod.POST,
                        "http://api.example.com/v1/categories/", "{}", 201, "{\"categoryId\":\"cat-9\"}", null, null)));
        Assertions.assertThat(ObservedHttpTarget.ownedPath(pool, args, "/categories/{id}",
                "http://api.example.com/v1/categories/cat-9")).contains("/categories/cat-9");
    }

    private static RuntimeResourcePool.SuccessfulExchange exchange(String url, String location) {
        return new RuntimeResourcePool.SuccessfulExchange("/users", HttpMethod.POST, url, "{}", 201, "{}", location, null);
    }
}
