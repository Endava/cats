package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.io.util.MultipartEncoder;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

@QuarkusTest
class MultipartMalformedBoundaryFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private MultipartMalformedBoundaryFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        fuzzer = new MultipartMalformedBoundaryFuzzer(new SimpleExecutor(testCaseListener, serviceCaller));
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
    }

    @ParameterizedTest
    @CsvSource({"multipart/form-data, true", "multipart/form-data; boundary=abc, true", "application/json, false",
            "application/x-www-form-urlencoded, false", "application/xml, false", "text/plain, false",
            "application/x-ndjson, false"})
    void shouldOnlyApplyToMultipartPayloads(String contentType, boolean expected) {
        Assertions.assertThat(fuzzer.isApplicableTo(FuzzingData.builder().selectedRequestContentType(contentType).build()))
                .isEqualTo(expected);
    }

    @Test
    void shouldNotRunForGetAndEmptyPayload() {
        fuzzer.fuzz(MultipartFuzzerTestSupport.multipartData(HttpMethod.GET, MultipartFuzzerTestSupport.PAYLOAD));
        fuzzer.fuzz(MultipartFuzzerTestSupport.multipartData(HttpMethod.POST, ""));

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldSendThreeRequestsAndReportFourXx() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartData(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(400).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());

        fuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(Mockito.any(), Mockito.eq(data),
                Mockito.eq(response), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.eq(true));
        Assertions.assertThat(MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 3)).hasSize(3);
    }

    @Test
    void shouldBreakTheBoundaryInThreeWays() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartData(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
        fuzzer.fuzz(data);
        List<ServiceData> calls = MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 3);
        MultipartEncoder.Result encoded = MultipartFuzzerTestSupport.encoded();
        String boundary = encoded.boundary();

        MultipartEncoder.Result mismatched = MultipartFuzzerTestSupport.mutate(calls.get(0), encoded);
        Assertions.assertThat(mismatched.contentType()).isEqualTo(MultipartEncoder.contentType(boundary));
        Assertions.assertThat(MultipartFuzzerTestSupport.text(mismatched.body()))
                .startsWith("--" + boundary + "x\r\n").doesNotContain("--" + boundary + "\r\n");

        MultipartEncoder.Result unterminated = MultipartFuzzerTestSupport.mutate(calls.get(1), encoded);
        Assertions.assertThat(MultipartFuzzerTestSupport.text(unterminated.body())).doesNotContain("--" + boundary + "--")
                .endsWith("\r\n");
        Assertions.assertThat(unterminated.body()).hasSizeLessThan(encoded.body().length);

        MultipartEncoder.Result noCrlf = MultipartFuzzerTestSupport.mutate(calls.get(2), encoded);
        String noCrlfBody = MultipartFuzzerTestSupport.text(noCrlf.body());
        Assertions.assertThat(noCrlfBody).startsWith("--" + boundary + "Content-Disposition");
        Assertions.assertThat(noCrlf.body()).hasSize(encoded.body().length - 2);
        Assertions.assertThat(noCrlfBody).endsWith("--" + boundary + "--\r\n");
        Assertions.assertThat(noCrlf.contentType()).isEqualTo(encoded.contentType());
    }

    @Test
    void shouldHaveToStringAndDescription() {
        Assertions.assertThat(fuzzer).hasToString(fuzzer.getClass().getSimpleName());
        Assertions.assertThat(fuzzer.description()).isNotBlank();
    }
}
