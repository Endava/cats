package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

@QuarkusTest
class IdempotencyKeyConflictFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private IdempotencyKeyConflictFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.when(testCaseListener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        fuzzer = new IdempotencyKeyConflictFuzzer(simpleExecutor, testCaseListener);
    }

    @Test
    void shouldSkipWhenNoIdempotencyHeaderPresent() {
        FuzzingData data = FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.POST)
                .reqSchema(new Schema<>())
                .requestContentTypes(List.of("application/json"))
                .payload("{\"item\":\"book\"}")
                .headers(Set.of(CatsHeader.builder().name("X-Request-Id").value("123").build()))
                .allFieldsByHttpMethod(Set.of("item"))
                .build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldReplaySameIdempotencyKeyWithMutatedPayloadWhenInitialRequestSucceeds() {
        CatsResponse firstResponse = CatsResponse.builder().responseCode(201).body("{\"id\":\"1\"}").build();
        CatsResponse secondResponse = CatsResponse.builder().responseCode(409).body("{\"error\":\"conflict\"}").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(firstResponse, secondResponse);

        FuzzingData data = FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.POST)
                .reqSchema(new Schema<>())
                .requestContentTypes(List.of("application/json"))
                .payload("{\"item\":\"book\"}")
                .headers(Set.of(CatsHeader.builder().name("Idempotency-Key").value("initial").build()))
                .allFieldsByHttpMethod(Set.of("item"))
                .build();

        fuzzer.fuzz(data);

        Mockito.verify(serviceCaller, Mockito.times(2)).call(Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("Initial request with {} succeeded with status {}"),
                Mockito.eq("Idempotency-Key"), Mockito.eq("201"));
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(
                Mockito.any(), Mockito.eq(data), Mockito.eq(secondResponse),
                Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void shouldReport5xxAndStopWhenInitialRequestCrashes() {
        CatsResponse crashResponse = CatsResponse.builder().responseCode(500).body("{\"error\":\"boom\"}").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(crashResponse);

        FuzzingData data = FuzzingData.builder()
                .path("/orders")
                .method(HttpMethod.POST)
                .reqSchema(new Schema<>())
                .requestContentTypes(List.of("application/json"))
                .payload("{\"item\":\"book\"}")
                .headers(Set.of(CatsHeader.builder().name("Idempotency-Key").value("initial").build()))
                .allFieldsByHttpMethod(Set.of("item"))
                .build();

        fuzzer.fuzz(data);

        Mockito.verify(serviceCaller, Mockito.times(1)).call(Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(
                Mockito.any(), Mockito.eq(data), Mockito.eq(crashResponse),
                Mockito.eq(ResponseCodeFamilyPredefined.TWOXX));
    }

    @Test
    void shouldHaveDescriptionAndSkipMethods() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("IdempotencyKeyConflictFuzzer");
        Assertions.assertThat(fuzzer.skipForHttpMethods())
                .containsOnly(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE, HttpMethod.PUT);
    }
}
