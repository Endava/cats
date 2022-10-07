package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

        Assertions.assertThat(headers).hasSize(1);
        Assertions.assertThat(headers.get(0)).contains(CatsHeader.builder().name("Content-Type").build());
    }

    @Test
    void shouldReturnHttpMethodsSkipFor() {
        Assertions.assertThat(dummyContentTypeHeadersFuzzer.skipForHttpMethods()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Test
    void shouldNotRunWithEmptyPayload() {
        dummyContentTypeHeadersFuzzer.fuzz(Mockito.mock(FuzzingData.class));

        Mockito.verifyNoInteractions(simpleExecutor);
    }
}
