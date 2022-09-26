package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.StringSchema;
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
class UnsupportedContentTypesHeadersFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private UnsupportedContentTypesHeadersFuzzer unsupportedContentTypeHeadersFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        unsupportedContentTypeHeadersFuzzer = new UnsupportedContentTypesHeadersFuzzer(simpleExecutor);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldProperlyOverrideParentMethods() {
        Assertions.assertThat(unsupportedContentTypeHeadersFuzzer.typeOfHeader()).isEqualTo("unsupported");
        Assertions.assertThat(unsupportedContentTypeHeadersFuzzer.description()).isEqualTo("send a request with a unsupported Content-Type header and expect to get 415 code");
        Assertions.assertThat(unsupportedContentTypeHeadersFuzzer).hasToString(unsupportedContentTypeHeadersFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnAValidHeadersList() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .requestContentTypes(Collections.singletonList("application/json")).build();

        List<Set<CatsHeader>> headers = unsupportedContentTypeHeadersFuzzer.getHeaders(data);

        Assertions.assertThat(headers).hasSize(29);
        Assertions.assertThat(headers.get(0)).contains(CatsHeader.builder().name("Content-Type").build());
    }

    @Test
    void shouldRunFuzzerForEachSetOfHeaders() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .requestContentTypes(Collections.singletonList("application/json")).reqSchema(new StringSchema()).build();
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.FOURXX_MT));
        unsupportedContentTypeHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(29)).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.FOURXX_MT));
    }
}
