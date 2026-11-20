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
import java.util.Map;

@QuarkusTest
class MultipartMissingPartHeadersFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private MultipartMissingPartHeadersFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        fuzzer = new MultipartMissingPartHeadersFuzzer(new SimpleExecutor(testCaseListener, serviceCaller));
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
    void shouldSendTwoRequestsPerPartAndReportFourXx() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartData(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(400).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());

        fuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(6)).reportResult(Mockito.any(), Mockito.eq(data),
                Mockito.eq(response), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.eq(true));
        Assertions.assertThat(MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 6)).hasSize(6);
    }

    @Test
    void shouldRemoveOrAnonymizeTheDispositionOfTheTargetedPart() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartData(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
        fuzzer.fuzz(data);
        List<ServiceData> calls = MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 6);
        MultipartEncoder.Result encoded = MultipartFuzzerTestSupport.encoded();
        String boundary = encoded.boundary();

        String fileNoHeaders = MultipartFuzzerTestSupport.text(MultipartFuzzerTestSupport.mutate(calls.get(0), encoded).body());
        Assertions.assertThat(fileNoHeaders).contains("--" + boundary + "\r\n\r\nhello\r\n")
                .doesNotContain("name=\"file\"").contains("name=\"evidenceType\"").contains("name=\"checksum\"");

        String fileNoName = MultipartFuzzerTestSupport.text(MultipartFuzzerTestSupport.mutate(calls.get(1), encoded).body());
        Assertions.assertThat(fileNoName).contains("Content-Disposition: form-data\r\nContent-Type: application/octet-stream\r\n\r\nhello")
                .doesNotContain("name=\"file\"").contains("name=\"evidenceType\"");

        String typeNoHeaders = MultipartFuzzerTestSupport.text(MultipartFuzzerTestSupport.mutate(calls.get(2), encoded).body());
        Assertions.assertThat(typeNoHeaders).contains("--" + boundary + "\r\n\r\nOTHER\r\n")
                .doesNotContain("name=\"evidenceType\"").contains("name=\"file\"; filename=\"file.bin\"");

        String typeNoName = MultipartFuzzerTestSupport.text(MultipartFuzzerTestSupport.mutate(calls.get(3), encoded).body());
        Assertions.assertThat(typeNoName).contains("Content-Disposition: form-data\r\n\r\nOTHER\r\n")
                .doesNotContain("name=\"evidenceType\"").contains("name=\"checksum\"");

        MultipartEncoder.Result withoutTargetPart = MultipartEncoder.encode("{\"evidenceType\":\"OTHER\"}", true, Map.of(), _ -> false);
        Assertions.assertThat(MultipartFuzzerTestSupport.mutate(calls.get(0), withoutTargetPart)).isSameAs(withoutTargetPart);
    }

    @Test
    void shouldHaveToStringAndDescription() {
        Assertions.assertThat(fuzzer).hasToString(fuzzer.getClass().getSimpleName());
        Assertions.assertThat(fuzzer.description()).isNotBlank();
    }
}
