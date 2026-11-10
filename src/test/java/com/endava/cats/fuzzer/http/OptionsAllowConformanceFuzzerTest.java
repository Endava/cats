package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

@QuarkusTest
class OptionsAllowConformanceFuzzerTest {
    private SimpleExecutor executor;
    private TestCaseListener listener;
    private OptionsAllowConformanceFuzzer fuzzer;

    @BeforeEach
    void setup() {
        executor = Mockito.mock(SimpleExecutor.class);
        listener = Mockito.mock(TestCaseListener.class);
        fuzzer = new OptionsAllowConformanceFuzzer(executor, listener);
    }

    @Test
    void shouldSendSingleOptionsRequestPerPathWithoutCorsHeaders() {
        FuzzingData data = data(HttpMethod.GET);

        fuzzer.fuzz(data);
        fuzzer.fuzz(data(HttpMethod.POST));

        SimpleExecutorContext context = captureContext();
        Assertions.assertThat(context.getHttpMethod()).isEqualTo(HttpMethod.OPTIONS);
        Assertions.assertThat(context.getPayload()).isEmpty();
        Assertions.assertThat(context.getSkippedHeaders()).contains("Origin", "Access-Control-Request-Method");
        Assertions.assertThat(context.getMutationTargets()).containsExactly(RequestTarget.httpMethod());
    }

    @Test
    void shouldSkipWhenPathItemMissing() {
        fuzzer.fuzz(FuzzingData.builder().method(HttpMethod.GET).path("/items").build());

        Mockito.verifyNoInteractions(executor);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "GET, POST|[]|[]",
            "get,post,head,options|[]|[]",
            "GET, POST, DELETE|[DELETE]|[]",
            "GET|[]|[POST]",
            "GET, PUT, FOO|[FOO, PUT]|[POST]"}, delimiter = '|')
    void shouldCompareAllowHeaderWithDocumentedMethods(String allow, String undocumented, String missing) {
        FuzzingData data = data(HttpMethod.GET);
        fuzzer.fuzz(data);

        captureContext().getResponseProcessor().accept(response(200, allow), data);

        if (undocumented.equals("[]") && missing.equals("[]")) {
            Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.anyString());
        } else {
            ArgumentCaptor<Object> sets = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(listener).reportResultWarn(Mockito.any(), Mockito.eq(data),
                    Mockito.eq(Reason.OPTIONS_ALLOW_MISMATCH.value()), Mockito.anyString(), sets.capture(), sets.capture());
            Assertions.assertThat(sets.getAllValues()).extracting(Object::toString).containsExactly(undocumented, missing);
        }
    }

    @ParameterizedTest
    @CsvSource({"405,GET", "404,", "200,", "204,"})
    void shouldReportInfoWhenAllowIsUnavailable(int status, String allow) {
        FuzzingData data = data(HttpMethod.GET);
        fuzzer.fuzz(data);

        captureContext().getResponseProcessor().accept(response(status, allow), data);

        Mockito.verify(listener).reportResultInfo(Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.eq(String.valueOf(status)));
        Mockito.verify(listener, Mockito.never()).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any(Object[].class));
    }

    @Test
    void shouldReportServerError() {
        FuzzingData data = data(HttpMethod.GET);
        fuzzer.fuzz(data);

        captureContext().getResponseProcessor().accept(response(500, "GET"), data);

        Mockito.verify(listener).reportResultError(Mockito.any(), Mockito.eq(data), Mockito.eq(Reason.SERVER_ERROR.value()),
                Mockito.anyString(), Mockito.eq("500"));
    }

    @Test
    void shouldListDocumentedMethodsIncludingOptions() {
        PathItem item = new PathItem().get(new Operation()).delete(new Operation()).options(new Operation());

        Assertions.assertThat(OptionsAllowConformanceFuzzer.documentedMethods(item)).containsExactly("DELETE", "GET", "OPTIONS");
    }

    @Test
    void shouldReturnMetadata() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.skipForHttpMethods()).isEmpty();
        Assertions.assertThat(fuzzer).hasToString("OptionsAllowConformanceFuzzer");
    }

    private SimpleExecutorContext captureContext() {
        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(executor).execute(captor.capture());
        return captor.getValue();
    }

    private static CatsResponse response(int status, String allow) {
        List<KeyValuePair<String, String>> headers = allow == null ? List.of() : List.of(new KeyValuePair<>("allow", allow));
        return CatsResponse.builder().responseCode(status).headers(headers).build();
    }

    private static FuzzingData data(HttpMethod method) {
        return FuzzingData.builder().method(method).path("/items").contractPath("/items")
                .pathItem(new PathItem().get(new Operation()).post(new Operation()))
                .headers(Set.of()).payload("{}").build();
    }
}
