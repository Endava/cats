package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
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
class DummyTransferEncodingHeadersFuzzerTest {
    private DummyTransferEncodingHeadersFuzzer dummyTransferEncodingHeadersFuzzer;

    @BeforeEach
    void setup() {
        dummyTransferEncodingHeadersFuzzer = new DummyTransferEncodingHeadersFuzzer(Mockito.mock(SimpleExecutor.class));
    }

    @Test
    void shouldProperlyOverrideParentMethods() {
        Assertions.assertThat(dummyTransferEncodingHeadersFuzzer.typeOfHeader()).isEqualTo("dummy");
        Assertions.assertThat(dummyTransferEncodingHeadersFuzzer.description()).isEqualTo("send a request with a dummy Transfer-Encoding header and expect to get 400|501 code");
        Assertions.assertThat(dummyTransferEncodingHeadersFuzzer).hasToString(dummyTransferEncodingHeadersFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnAValidHeadersList() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .responseContentTypes(Collections.singletonMap("200", Collections.singletonList("application/json"))).build();

        List<Set<CatsHeader>> headers = dummyTransferEncodingHeadersFuzzer.getHeaders(data);

        Assertions.assertThat(headers).hasSize(1);
        Assertions.assertThat(headers.get(0)).contains(CatsHeader.builder().name("Transfer-Encoding").build());
    }

    @Test
    void shouldReturn400501ResponseCode() {
        Assertions.assertThat(dummyTransferEncodingHeadersFuzzer.getResponseCodeFamily()).isEqualTo(ResponseCodeFamily.FOUR00_FIVE01);
    }
}