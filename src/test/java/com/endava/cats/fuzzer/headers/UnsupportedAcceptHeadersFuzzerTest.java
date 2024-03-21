package com.endava.cats.fuzzer.headers;

import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@QuarkusTest
class UnsupportedAcceptHeadersFuzzerTest {
    public static final List<CatsHeader> HEADERS = Arrays.asList(CatsHeader.builder().name("Cache-Control").value("no-store").build(),
            CatsHeader.builder().name("X-Content-Type-Options").value("nosniff").build(),
            CatsHeader.builder().name("X-Frame-Options").value("DENY").build());
    private UnsupportedAcceptHeadersFuzzer unsupportedAcceptHeadersFuzzer;

    @BeforeEach
    void setup() {
        unsupportedAcceptHeadersFuzzer = new UnsupportedAcceptHeadersFuzzer(null);
    }

    @Test
    void shouldProperlyOverrideParentMethods() {
        Assertions.assertThat(unsupportedAcceptHeadersFuzzer.typeOfHeader()).isEqualTo("unsupported");
        Assertions.assertThat(unsupportedAcceptHeadersFuzzer.description()).isEqualTo("send a request with a unsupported Accept header and expect to get 406 code");
        Assertions.assertThat(unsupportedAcceptHeadersFuzzer).hasToString(unsupportedAcceptHeadersFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnAValidHeadersList() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .responseContentTypes(Collections.singletonMap("200", Collections.singletonList("application/json"))).build();

        List<Set<CatsHeader>> headers = unsupportedAcceptHeadersFuzzer.getHeaders(data);

        Assertions.assertThat(headers).hasSize(29);
        Assertions.assertThat(headers.get(0)).contains(CatsHeader.builder().name("Accept").build());
    }

    @Test
    void shouldReturn4XXMTResponseCode() {
        Assertions.assertThat(unsupportedAcceptHeadersFuzzer.getResponseCodeFamily()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX_MT);
    }
}
