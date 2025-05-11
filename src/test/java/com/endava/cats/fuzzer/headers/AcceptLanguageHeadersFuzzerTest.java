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
import java.util.List;
import java.util.Set;


@QuarkusTest
class AcceptLanguageHeadersFuzzerTest {
    private AcceptLanguageHeadersFuzzer acceptLanguageHeadersFuzzer;

    @BeforeEach
    void setup() {
        acceptLanguageHeadersFuzzer = new AcceptLanguageHeadersFuzzer(Mockito.mock(SimpleExecutor.class));
    }

    @Test
    void shouldProperlyOverrideParentMethods() {
        Assertions.assertThat(acceptLanguageHeadersFuzzer.typeOfHeader()).isEqualTo("locale");
        Assertions.assertThat(acceptLanguageHeadersFuzzer.targetHeaderName()).isEqualToIgnoringCase("Accept-Language");
        Assertions.assertThat(acceptLanguageHeadersFuzzer.getExpectedResponseCode()).isEqualTo("200");
        Assertions.assertThat(acceptLanguageHeadersFuzzer.description()).isEqualTo("send a request with a locale Accept-Language header and expect to get 200 code");
        Assertions.assertThat(acceptLanguageHeadersFuzzer).hasToString(acceptLanguageHeadersFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnAValidHeadersList() {
        FuzzingData data = FuzzingData.builder().headers(Set.of())
                .responseContentTypes(Collections.singletonMap("200", Collections.singletonList("application/json"))).build();

        List<Set<CatsHeader>> headers = acceptLanguageHeadersFuzzer.getHeaders(data);

        Assertions.assertThat(headers).hasSize(5);
        Assertions.assertThat(headers.getFirst()).contains(CatsHeader.builder().name("Accept-Language").build());
    }

    @Test
    void shouldReturnTwoXXResponseCode() {
        Assertions.assertThat(acceptLanguageHeadersFuzzer.getResponseCodeFamily()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
    }
}
