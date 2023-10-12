package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.KeyValuePair;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.endava.cats.fuzzer.headers.CheckSecurityHeadersFuzzer.SECURITY_HEADERS;
import static com.endava.cats.fuzzer.headers.CheckSecurityHeadersFuzzer.SECURITY_HEADERS_AS_STRING;
import static com.endava.cats.fuzzer.headers.UnsupportedAcceptHeadersFuzzerTest.HEADERS;

@QuarkusTest
class CheckSecurityHeadersFuzzerTest {

    private static final List<KeyValuePair<String, String>> SOME_SECURITY_HEADERS = Arrays.asList(new KeyValuePair<>("Cache-Control", "no-store"),
            new KeyValuePair<>("X-Content-Type-Options", "nosniff"));
    private static final List<KeyValuePair<String, String>> MISSING_HEADERS = Arrays.asList(new KeyValuePair<>("X-Frame-Options", "DENY"),
            new KeyValuePair<>("Content-Security-Policy", "frame-ancestors 'none'"));
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private CheckSecurityHeadersFuzzer checkSecurityHeadersFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        SimpleExecutor simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        checkSecurityHeadersFuzzer = new CheckSecurityHeadersFuzzer(testCaseListener, simpleExecutor);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(checkSecurityHeadersFuzzer.description()).isEqualTo("check all responses for good practices around Security related headers like: " + SECURITY_HEADERS_AS_STRING);
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(checkSecurityHeadersFuzzer).hasToString(checkSecurityHeadersFuzzer.getClass().getSimpleName());
    }


    @Test
    void shouldReportMissingSecurityHeaders() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .requestContentTypes(Collections.singletonList("application/json")).reqSchema(new StringSchema()).build();
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
        Mockito.doNothing().when(testCaseListener).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).headers(SOME_SECURITY_HEADERS).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        checkSecurityHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.eq("Missing recommended Security Headers: {}"), AdditionalMatchers.aryEq(new Object[]{MISSING_HEADERS.stream().map(Object::toString).collect(Collectors.toSet())}));
    }

    @Test
    void shouldNotReportMissingSecurityHeaders() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .requestContentTypes(Collections.singletonList("application/json")).reqSchema(new StringSchema()).build();
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
        Mockito.doNothing().when(testCaseListener).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        List<KeyValuePair<String, String>> allHeaders = new ArrayList<>(SOME_SECURITY_HEADERS);
        allHeaders.add(new KeyValuePair<>("dummy", "dummy"));

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).headers(Stream.concat(allHeaders.stream(), MISSING_HEADERS.stream())
                .collect(Collectors.toList())).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        checkSecurityHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void shouldNotReportMissingHeadersWhenCSP() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .requestContentTypes(Collections.singletonList("application/json")).reqSchema(new StringSchema()).build();
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
        Mockito.doNothing().when(testCaseListener).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        List<KeyValuePair<String, String>> allHeaders = new ArrayList<>(SOME_SECURITY_HEADERS);
        allHeaders.add(new KeyValuePair<>("Content-Security-Policy", "frame-ancestors 'none'"));
        allHeaders.add(new KeyValuePair<>("X-XSS-Protection", "0"));

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).headers(allHeaders).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        checkSecurityHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void shouldReportMismatchingXXSSProtection() {
        FuzzingData data = FuzzingData.builder().headers(new HashSet<>(HEADERS))
                .requestContentTypes(Collections.singletonList("application/json")).reqSchema(new StringSchema()).build();
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(),
                Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamily.TWOXX));
        Mockito.doNothing().when(testCaseListener).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        List<KeyValuePair<String, String>> allHeaders = new ArrayList<>(SOME_SECURITY_HEADERS);
        allHeaders.add(new KeyValuePair<>("Content-Security-Policy", "frame-ancestors 'none'"));
        allHeaders.add(new KeyValuePair<>("X-XSS-Protection", "02"));

        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).headers(allHeaders).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);

        checkSecurityHeadersFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.eq("Missing recommended Security Headers: {}"), AdditionalMatchers.aryEq(new Object[]{SECURITY_HEADERS.get("X-XSS-Protection").stream().map(Object::toString).collect(Collectors.toSet())}));
    }
}
