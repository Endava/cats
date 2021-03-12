package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.endava.cats.fuzzer.headers.CheckSecurityHeadersFuzzer.SECURITY_HEADERS_AS_STRING;
import static com.endava.cats.fuzzer.headers.UnsupportedAcceptHeadersFuzzerTest.HEADERS;

@ExtendWith(SpringExtension.class)
class CheckSecurityHeadersFuzzerTest {

    private static final List<CatsHeader> SOME_SECURITY_HEADERS = Arrays.asList(CatsHeader.builder().name("Cache-Control").value("no-store").build(),
            CatsHeader.builder().name("X-Content-Type-Options").value("nosniff").build());
    private static final List<CatsHeader> MISSING_HEADERS = Arrays.asList(CatsHeader.builder().name("X-Frame-Options").value("DENY").build(),
            CatsHeader.builder().name("X-XSS-Protection").value("1; mode=block").build());
    @Mock
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;


    private CheckSecurityHeadersFuzzer checkSecurityHeadersFuzzer;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
        checkSecurityHeadersFuzzer = new CheckSecurityHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void shouldProperlyOverrideParentMethods() {
        Assertions.assertThat(checkSecurityHeadersFuzzer.description()).isEqualTo("check all responses for good practices around Security related headers like: " + SECURITY_HEADERS_AS_STRING);
        Assertions.assertThat(checkSecurityHeadersFuzzer).hasToString(checkSecurityHeadersFuzzer.getClass().getSimpleName());
    }


    @Test
    void shouldReportMissingSecurityHeaders() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .requestContentTypes(Collections.singletonList("application/json")).build();
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
        Mockito.doNothing().when(testCaseListener).reportError(Mockito.any(), Mockito.any());

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).headers(SOME_SECURITY_HEADERS).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);

        checkSecurityHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.eq("Missing recommended Security Headers: {}")
                , Mockito.eq(MISSING_HEADERS.stream().map(CatsHeader::nameAndValue).collect(Collectors.toSet())));
    }

    @Test
    void shouldNotReportMissingSecurityHeaders() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .requestContentTypes(Collections.singletonList("application/json")).build();
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
        Mockito.doNothing().when(testCaseListener).reportError(Mockito.any(), Mockito.any());
        List<CatsHeader> allHeaders = new ArrayList<>(SOME_SECURITY_HEADERS);
        allHeaders.add(CatsHeader.builder().name("dummy").value("dummy").build());

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).headers(Stream.concat(allHeaders.stream(), MISSING_HEADERS.stream())
                .collect(Collectors.toList())).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);

        checkSecurityHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }
}
