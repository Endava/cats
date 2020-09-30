package com.endava.cats.fuzzer.headers;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

@ExtendWith(SpringExtension.class)
class UnsupportedAcceptHeadersFuzzerTest {
    public static final List<CatsHeader> HEADERS = Arrays.asList(CatsHeader.builder().name("Cache-Control").value("no-store").build(),
            CatsHeader.builder().name("X-Content-Type-Options").value("nosniff").build(),
            CatsHeader.builder().name("X-Frame-Options").value("DENY").build());
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private UnsupportedAcceptHeadersFuzzer unsupportedAcceptHeadersFuzzer;

    @BeforeEach
    void setup() {
        unsupportedAcceptHeadersFuzzer = new UnsupportedAcceptHeadersFuzzer(serviceCaller, testCaseListener);
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

        Assertions.assertThat(headers).hasSize(BaseSecurityChecksHeadersFuzzer.UNSUPPORTED_MEDIA_TYPES.size() - 1);
        Assertions.assertThat(headers.get(0)).contains(CatsHeader.builder().name("Accept").build());
    }
}
