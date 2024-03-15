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
class DummyAcceptHeadersFuzzerTest {
    private DummyAcceptHeadersFuzzer dummyAcceptHeadersFuzzer;

    @BeforeEach
    void setup() {
        dummyAcceptHeadersFuzzer = new DummyAcceptHeadersFuzzer(Mockito.mock(SimpleExecutor.class));
    }

    @Test
    void shouldProperlyOverrideParentMethods() {
        Assertions.assertThat(dummyAcceptHeadersFuzzer.typeOfHeader()).isEqualTo("dummy");
        Assertions.assertThat(dummyAcceptHeadersFuzzer.description()).isEqualTo("send a request with a dummy Accept header and expect to get 406 code");
        Assertions.assertThat(dummyAcceptHeadersFuzzer).hasToString(dummyAcceptHeadersFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnAValidHeadersList() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .responseContentTypes(Collections.singletonMap("200", Collections.singletonList("application/json"))).build();

        List<Set<CatsHeader>> headers = dummyAcceptHeadersFuzzer.getHeaders(data);

        Assertions.assertThat(headers).hasSize(1);
        Assertions.assertThat(headers.get(0)).contains(CatsHeader.builder().name("Accept").build());
    }

    @Test
    void shouldReturn4XXMTResponseCode() {
        Assertions.assertThat(dummyAcceptHeadersFuzzer.getResponseCodeFamily()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX_MT);
    }
}
