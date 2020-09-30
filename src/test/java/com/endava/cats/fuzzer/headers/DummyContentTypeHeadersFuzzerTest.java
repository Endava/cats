package com.endava.cats.fuzzer.headers;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.endava.cats.fuzzer.headers.UnsupportedAcceptHeadersFuzzerTest.HEADERS;

class DummyContentTypeHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private DummyContentTypeHeadersFuzzer dummyContentTypeHeadersFuzzer;

    @BeforeEach
    void setup() {
        dummyContentTypeHeadersFuzzer = new DummyContentTypeHeadersFuzzer(serviceCaller, testCaseListener);
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
}
