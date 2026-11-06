package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@QuarkusTest
class PaginationLimitHonoredFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private TestCaseListener testCaseListener;
    private PaginationLimitHonoredFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        fuzzer = new PaginationLimitHonoredFuzzer(simpleExecutor, testCaseListener);
    }

    @Test
    void shouldWarnWhenMoreThanOneItemReturnedForLimitOne() {
        FuzzingData data = FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.GET)
                .payload("{\"limit\":20,\"page\":1}")
                .queryParams(Set.of("limit", "page"))
                .requestPropertyTypes(Map.of("limit", new IntegerSchema(), "page", new IntegerSchema()))
                .build();

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        SimpleExecutorContext context = captor.getValue();
        Assertions.assertThat(context.getPayload()).contains("\"limit\":1");

        CatsResponse response = CatsResponse.builder()
                .responseCode(200)
                .body("{\"items\":[{\"id\":1},{\"id\":2},{\"id\":3}],\"total\":3}")
                .build();
        context.getResponseProcessor().accept(response, data);

        Mockito.verify(testCaseListener).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq(Reason.PAGINATION_LIMIT_IGNORED.value()),
                Mockito.anyString(),
                Mockito.eq("limit"),
                Mockito.eq("/orders"),
                Mockito.eq(3));
    }

    @Test
    void shouldReportInfoWhenAtMostOneItemReturned() {
        FuzzingData data = FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.GET)
                .payload("{\"pageSize\":10}")
                .queryParams(Set.of("pageSize"))
                .requestPropertyTypes(Map.of("pageSize", new IntegerSchema()))
                .build();

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        SimpleExecutorContext context = captor.getValue();

        CatsResponse response = CatsResponse.builder()
                .responseCode(200)
                .body("[{\"id\":1}]")
                .build();
        context.getResponseProcessor().accept(response, data);

        Mockito.verify(testCaseListener).reportResultInfo(
                Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.eq("pageSize"), Mockito.eq(1));
    }

    @Test
    void shouldSkipWhenMinimumExceedsOneOrNoLimitParamExists() {
        IntegerSchema minTenSchema = new IntegerSchema();
        minTenSchema.setMinimum(BigDecimal.TEN);

        FuzzingData data = FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.GET)
                .payload("{\"limit\":20}")
                .queryParams(Set.of("limit"))
                .requestPropertyTypes(Map.of("limit", minTenSchema))
                .build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldReturnMetadata() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("PaginationLimitHonoredFuzzer");
    }

    @ParameterizedTest
    @CsvSource({"500,{},false,error", "400,{},false,info", "200,{},true,info", "200,invalid,false,info", "200,{},false,info"})
    void shouldClassifyInconclusiveResponses(int status, String body, boolean truncated, String result) {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).path("/items")
                .payload("{\"limit\":10}").queryParams(Set.of("limit")).build();
        fuzzer.fuzz(data);
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        captor.getValue().getResponseProcessor().accept(CatsResponse.builder().responseCode(status).body(body).bodyTruncated(truncated).build(), data);
        if (result.equals("error")) {
            Mockito.verify(testCaseListener).reportResultError(Mockito.any(), Mockito.eq(data), Mockito.eq(Reason.SERVER_ERROR.value()), Mockito.anyString(), Mockito.eq("limit"), Mockito.eq("500"));
        } else {
            Mockito.verify(testCaseListener).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.any());
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"null|-1", "3|-1", "[]|0", "{\"items\":[1,2],\"other\":[]}|2", "{\"custom\":[1],\"meta\":null}|1", "{\"a\":[],\"b\":[]}|-1", "{}|-1"}, delimiter = '|')
    void shouldExtractOnlyInspectableCollections(String json, int size) {
        Assertions.assertThat(PaginationLimitHonoredFuzzer.extractCollectionSize(JsonUtils.parseAsJsonElement(json)))
                .isEqualTo(size < 0 ? Optional.empty() : Optional.of(size));
        Assertions.assertThat(PaginationLimitHonoredFuzzer.extractCollectionSize(null)).isEmpty();
    }

    @Test
    void shouldSelectOnlyPresentNumericLimitParameters() {
        FuzzingData data = FuzzingData.builder().payload("{\"limit\":2,\"size\":3,\"page\":1}")
                .queryParams(Set.of("limit", "size", "top", "page"))
                .requestPropertyTypes(Map.of("limit", new StringSchema(),
                        "size", new NumberSchema().minimum(BigDecimal.ONE))).build();
        Assertions.assertThat(PaginationLimitHonoredFuzzer.findEligibleLimitParam(data)).contains("size");
        Assertions.assertThat(PaginationLimitHonoredFuzzer.findEligibleLimitParam(FuzzingData.builder().payload("{}").build())).isEmpty();
        fuzzer.fuzz(FuzzingData.builder().method(HttpMethod.POST).build());
        Mockito.verifyNoInteractions(simpleExecutor);
    }

}
