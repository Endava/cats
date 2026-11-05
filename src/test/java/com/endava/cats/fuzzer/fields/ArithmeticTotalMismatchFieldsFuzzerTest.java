package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

@QuarkusTest
class ArithmeticTotalMismatchFieldsFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private TestCaseListener testCaseListener;
    private ArithmeticTotalMismatchFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        fuzzer = new ArithmeticTotalMismatchFieldsFuzzer(simpleExecutor, testCaseListener);
    }

    @Test
    void shouldReportErrorWhen2xxResponseReflectsTamperedTotal() {
        FuzzingData data = FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.POST)
                .payload("{\"unitPrice\":25,\"quantity\":2,\"totalAmount\":50}")
                .allFieldsByHttpMethod(Set.of("unitPrice", "quantity", "totalAmount"))
                .requestPropertyTypes(Map.of(
                        "unitPrice", new NumberSchema(),
                        "quantity", new IntegerSchema(),
                        "totalAmount", new NumberSchema()))
                .build();

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        SimpleExecutorContext context = captor.getValue();
        Assertions.assertThat(context.getPayload())
                .contains("\"unitPrice\":10")
                .contains("\"quantity\":2")
                .contains("\"totalAmount\":98765");

        CatsResponse vulnerableResponse = CatsResponse.builder()
                .responseCode(201)
                .body("{\"unitPrice\":10,\"quantity\":2,\"totalAmount\":98765}")
                .build();
        context.getResponseProcessor().accept(vulnerableResponse, data);

        Mockito.verify(testCaseListener).reportResultError(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq(CatsResultFactory.Reason.ARITHMETIC_TOTAL_MISMATCH_ACCEPTED.value()),
                Mockito.contains("totalAmount=98765"));
    }

    @Test
    void shouldReportInfoWhen2xxResponseRecomputesTotalOr4xxRejects() {
        FuzzingData data = FuzzingData.builder()
                .path("/invoices")
                .method(HttpMethod.POST)
                .payload("{\"subtotal\":100,\"taxAmount\":19,\"grandTotal\":119}")
                .allFieldsByHttpMethod(Set.of("subtotal", "taxAmount", "grandTotal"))
                .build();

        fuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(captor.capture());
        SimpleExecutorContext context = captor.getValue();

        CatsResponse recomputedResponse = CatsResponse.builder()
                .responseCode(200)
                .body("{\"subtotal\":10,\"taxAmount\":2,\"grandTotal\":12}")
                .build();
        context.getResponseProcessor().accept(recomputedResponse, data);

        CatsResponse rejectedResponse = CatsResponse.builder().responseCode(422).body("{}").build();
        context.getResponseProcessor().accept(rejectedResponse, data);

        Mockito.verify(testCaseListener, Mockito.times(2)).reportResultInfo(
                Mockito.any(), Mockito.eq(data), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void shouldSkipWhenNoArithmeticTripleExists() {
        FuzzingData data = FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.POST)
                .payload("{\"unitPrice\":25,\"name\":\"item\"}")
                .allFieldsByHttpMethod(Set.of("unitPrice", "name"))
                .build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldReturnMetadata() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsExactly(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
        Assertions.assertThat(fuzzer).hasToString("ArithmeticTotalMismatchFieldsFuzzer");
    }
}
