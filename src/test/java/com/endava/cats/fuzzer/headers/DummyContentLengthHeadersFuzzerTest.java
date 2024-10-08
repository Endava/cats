package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
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
class DummyContentLengthHeadersFuzzerTest {
    private DummyContentLengthHeadersFuzzer dummyContentLengthHeadersFuzzer;

    @BeforeEach
    void setup() {
        dummyContentLengthHeadersFuzzer = new DummyContentLengthHeadersFuzzer(Mockito.mock(SimpleExecutor.class));
    }

    @Test
    void shouldProperlyOverrideParentMethods() {
        Assertions.assertThat(dummyContentLengthHeadersFuzzer.typeOfHeader()).isEqualTo("dummy");
        Assertions.assertThat(dummyContentLengthHeadersFuzzer.description()).isEqualTo("send a request with a dummy Content-Length header and expect to get 400 code");
        Assertions.assertThat(dummyContentLengthHeadersFuzzer).hasToString(dummyContentLengthHeadersFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnAValidHeadersList() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .responseContentTypes(Collections.singletonMap("200", Collections.singletonList("application/json"))).build();

        List<Set<CatsHeader>> headers = dummyContentLengthHeadersFuzzer.getHeaders(data);

        Assertions.assertThat(headers).hasSize(1);
        Assertions.assertThat(headers.getFirst()).contains(CatsHeader.builder().name("Content-Length").build());
    }

    @Test
    void shouldReturn400501ResponseCode() {
        Assertions.assertThat(dummyContentLengthHeadersFuzzer.getResponseCodeFamily()).isEqualTo(ResponseCodeFamilyPredefined.FOUR00_FIVE01);
    }

    @Test
    void shouldNotMatchResponseContentType() {
        Assertions.assertThat(dummyContentLengthHeadersFuzzer.shouldMatchContentType()).isFalse();
    }
}