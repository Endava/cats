package com.endava.cats.fuzzer.headers;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.endava.cats.fuzzer.headers.UnsupportedAcceptHeadersFuzzerTest.HEADERS;

@ExtendWith(SpringExtension.class)
class UnsupportedContentTypesHeadersFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private FilterArguments filterArguments;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;


    private UnsupportedContentTypesHeadersFuzzer unsupportedContentTypeHeadersFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
        unsupportedContentTypeHeadersFuzzer = new UnsupportedContentTypesHeadersFuzzer(serviceCaller, testCaseListener);
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
                .requestContentTypes(Collections.singletonList("application/json")).build();
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.FOURXX_MT));
        unsupportedContentTypeHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(29)).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.FOURXX_MT));
    }
}
