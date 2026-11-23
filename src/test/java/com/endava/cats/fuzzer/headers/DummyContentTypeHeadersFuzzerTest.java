package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.endava.cats.fuzzer.headers.UnsupportedAcceptHeadersFuzzerTest.HEADERS;

@QuarkusTest
class DummyContentTypeHeadersFuzzerTest {

    private DummyContentTypeHeadersFuzzer dummyContentTypeHeadersFuzzer;

    private SimpleExecutor simpleExecutor;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        dummyContentTypeHeadersFuzzer = new DummyContentTypeHeadersFuzzer(simpleExecutor);
    }

    @Test
    void shouldProperlyOverrideParentMethods() {
        Assertions.assertThat(dummyContentTypeHeadersFuzzer.typeOfHeader()).isEqualTo("dummy");
        Assertions.assertThat(dummyContentTypeHeadersFuzzer.description()).isEqualTo("send a request with a dummy Content-Type header and expect to get 415 code");
        Assertions.assertThat(dummyContentTypeHeadersFuzzer).hasToString(dummyContentTypeHeadersFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnAValidHeadersList() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .responseContentTypes(Collections.singletonMap("200", Collections.singletonList("application/json"))).build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"id\": 1}");

        List<Set<CatsHeader>> headers = dummyContentTypeHeadersFuzzer.getHeaders(data);

        Assertions.assertThat(headers).hasSize(3);
        Assertions.assertThat(headers.getFirst()).contains(CatsHeader.builder().name("Content-Type").build());

        FuzzingData getData = FuzzingData.builder().method(HttpMethod.GET).headers(new HashSet<>(HEADERS))
                .responseContentTypes(Collections.singletonMap("200", Collections.singletonList("application/json"))).build();
        ReflectionTestUtils.setField(getData, "processedPayload", "{\"id\": 1}");
        Assertions.assertThat(dummyContentTypeHeadersFuzzer.getHeaders(getData)).hasSize(2);
    }

    @Test
    void shouldReturnHttpMethodsSkipFor() {
        Assertions.assertThat(dummyContentTypeHeadersFuzzer.skipForHttpMethods()).containsOnly(HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Test
    void shouldNotRunWithEmptyPayload() {
        dummyContentTypeHeadersFuzzer.fuzz(Mockito.mock(FuzzingData.class));

        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldExpectAny4XXForMultipartWithoutBoundaryOnMultipartOperations() {
        FuzzingData data = payloadData(HttpMethod.POST, "multipart/form-data");

        List<SimpleExecutorContext> contexts = executedContexts(data);

        Assertions.assertThat(contexts).hasSize(3);
        Assertions.assertThat(contexts).filteredOn(context -> contentType(context).equals("multipart/form-data"))
                .singleElement().satisfies(context -> {
                    Assertions.assertThat(context.getExpectedResponseCode()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
                    Assertions.assertThat(context.getExpectedSpecificResponseCode()).isEqualTo("4XX");
                });
        Assertions.assertThat(contexts).filteredOn(context -> !contentType(context).equals("multipart/form-data"))
                .hasSize(2).allSatisfy(context -> {
                    Assertions.assertThat(context.getExpectedResponseCode()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX_MT);
                    Assertions.assertThat(context.getExpectedSpecificResponseCode()).isEqualTo("415");
                });
    }

    @Test
    void shouldExpect415ForAllContentTypesOnJsonOperations() {
        List<SimpleExecutorContext> contexts = executedContexts(payloadData(HttpMethod.POST, "application/json"));

        Assertions.assertThat(contexts).hasSize(3).allSatisfy(context -> {
            Assertions.assertThat(context.getExpectedResponseCode()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX_MT);
            Assertions.assertThat(context.getExpectedSpecificResponseCode()).isEqualTo("415");
        });
    }

    @Test
    void shouldExpect2XXOr4XXForAllContentTypesOnBodylessOperations() {
        List<SimpleExecutorContext> contexts = executedContexts(payloadData(HttpMethod.GET, "multipart/form-data"));

        Assertions.assertThat(contexts).hasSize(2).allSatisfy(context -> {
            Assertions.assertThat(context.getExpectedResponseCode()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX_TWOXX);
            Assertions.assertThat(context.getExpectedSpecificResponseCode()).isEqualTo("2XX or 4XX");
        });
    }

    private List<SimpleExecutorContext> executedContexts(FuzzingData data) {
        dummyContentTypeHeadersFuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> captor = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor, Mockito.atLeastOnce()).execute(captor.capture());
        return captor.getAllValues();
    }

    private static String contentType(SimpleExecutorContext context) {
        return context.getHeaders().stream().filter(header -> header.getName().equalsIgnoreCase("Content-Type"))
                .findFirst().orElseThrow().getValue();
    }

    private static FuzzingData payloadData(HttpMethod method, String requestContentType) {
        FuzzingData data = FuzzingData.builder().method(method).headers(new HashSet<>(HEADERS))
                .requestContentTypes(List.of(requestContentType))
                .responseContentTypes(Collections.singletonMap("200", Collections.singletonList("application/json"))).build();
        ReflectionTestUtils.setField(data, "processedPayload", "{\"id\": 1}");
        return data;
    }

    @Test
    void shouldReturn4XXMTResponseCode() {
        Assertions.assertThat(dummyContentTypeHeadersFuzzer.getResponseCodeFamily()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX_MT);
        FuzzingData getData = FuzzingData.builder().method(HttpMethod.GET).build();
        FuzzingData postData = FuzzingData.builder().method(HttpMethod.POST).build();
        Assertions.assertThat(dummyContentTypeHeadersFuzzer.getResponseCodeFamily(getData))
                .isEqualTo(ResponseCodeFamilyPredefined.FOURXX_TWOXX);
        Assertions.assertThat(dummyContentTypeHeadersFuzzer.getExpectedResponseCode(getData))
                .isEqualTo("2XX or 4XX");
        Assertions.assertThat(dummyContentTypeHeadersFuzzer.getResponseCodeFamily(postData))
                .isEqualTo(ResponseCodeFamilyPredefined.FOURXX_MT);
        Assertions.assertThat(dummyContentTypeHeadersFuzzer.getExpectedResponseCode(postData))
                .isEqualTo("415");
    }
}
