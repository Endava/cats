package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
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
class InvalidContentLengthHeadersFuzzerTest {
    private InvalidContentLengthHeadersFuzzer invalidContentLengthHeadersFuzzer;

    @BeforeEach
    void setup() {
        invalidContentLengthHeadersFuzzer = new InvalidContentLengthHeadersFuzzer(Mockito.mock(SimpleExecutor.class));
    }

    @Test
    void shouldProperlyOverrideParentMethods() {
        Assertions.assertThat(invalidContentLengthHeadersFuzzer.typeOfHeader()).isEqualTo("invalid");
        Assertions.assertThat(invalidContentLengthHeadersFuzzer.description()).isEqualTo("send a request with a invalid Content-Length header and expect to get 400 code");
        Assertions.assertThat(invalidContentLengthHeadersFuzzer).hasToString(invalidContentLengthHeadersFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnAValidHeadersList() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .responseContentTypes(Collections.singletonMap("200", Collections.singletonList("application/json"))).build();

        List<Set<CatsHeader>> headers = invalidContentLengthHeadersFuzzer.getHeaders(data);

        Assertions.assertThat(headers).hasSize(1);
        Assertions.assertThat(headers.get(0)).contains(CatsHeader.builder().name("Content-Length").build());
    }

    @Test
    void shouldReturn400501ResponseCode() {
        Assertions.assertThat(invalidContentLengthHeadersFuzzer.getResponseCodeFamily()).isEqualTo(ResponseCodeFamily.FOUR00_FIVE01);
    }

    @Test
    void shouldSkipForGetAndDelete() {
        Assertions.assertThat(invalidContentLengthHeadersFuzzer.skipForHttpMethods()).contains(HttpMethod.GET, HttpMethod.DELETE);
    }
}
