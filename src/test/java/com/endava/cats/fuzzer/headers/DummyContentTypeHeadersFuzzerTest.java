package com.endava.cats.fuzzer.headers;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.endava.cats.fuzzer.headers.UnsupportedAcceptHeadersFuzzerTest.HEADERS;

@QuarkusTest
class DummyContentTypeHeadersFuzzerTest {

    private DummyContentTypeHeadersFuzzer dummyContentTypeHeadersFuzzer;

    @BeforeEach
    void setup() {
        dummyContentTypeHeadersFuzzer = new DummyContentTypeHeadersFuzzer(Mockito.mock(ServiceCaller.class), Mockito.mock(TestCaseListener.class));
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

        List<Set<CatsHeader>> headers = dummyContentTypeHeadersFuzzer.getHeaders(data);

        Assertions.assertThat(headers).hasSize(1);
        Assertions.assertThat(headers.get(0)).contains(CatsHeader.builder().name("Content-Type").build());
    }

    @Test
    void shouldReturnHttpMethodsSkipFor() {
        Assertions.assertThat(dummyContentTypeHeadersFuzzer.skipForHttpMethods()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);
    }
}
