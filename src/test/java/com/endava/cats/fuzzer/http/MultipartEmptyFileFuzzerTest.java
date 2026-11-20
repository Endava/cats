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
class MultipartEmptyFileFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private MultipartEmptyFileFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        fuzzer = new MultipartEmptyFileFuzzer(new SimpleExecutor(testCaseListener, serviceCaller));
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
    void shouldNotRunForGetEmptyPayloadOrWithoutBinaryProperties() {
        fuzzer.fuzz(MultipartFuzzerTestSupport.multipartDataWithBinaryFile(HttpMethod.GET, MultipartFuzzerTestSupport.PAYLOAD));
        fuzzer.fuzz(MultipartFuzzerTestSupport.multipartDataWithBinaryFile(HttpMethod.POST, ""));
        fuzzer.fuzz(MultipartFuzzerTestSupport.multipartData(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD));

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldSendOneRequestPerFilePartAndReportFourXx() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartDataWithBinaryFile(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(422).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());

        fuzzer.fuzz(data);

        Mockito.verify(testCaseListener).addScenario(Mockito.any(), Mockito.eq("Send a multipart request with an empty [file] file"), Mockito.any(Object[].class));
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(response),
                Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.eq(true));
        Assertions.assertThat(MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 1)).hasSize(1);
    }

    @Test
    void shouldEmptyTheFilePartAndKeepItsHeaders() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartDataWithBinaryFile(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
        fuzzer.fuzz(data);
        List<ServiceData> calls = MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 1);
        MultipartEncoder.Result encoded = MultipartFuzzerTestSupport.encoded();

        MultipartEncoder.Result mutated = MultipartFuzzerTestSupport.mutate(calls.getFirst(), encoded);

        String body = MultipartFuzzerTestSupport.text(mutated.body());
        Assertions.assertThat(body).contains("name=\"file\"; filename=\"file.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n\r\n--"
                + encoded.boundary())
                .doesNotContain("hello").contains("name=\"evidenceType\"");
        Assertions.assertThat(mutated.contentType()).isEqualTo(encoded.contentType());
    }

    @Test
    void shouldLeaveTheBodyUnchangedWhenThereIsNoFilePartToEmpty() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartDataWithBinaryFile(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
        fuzzer.fuzz(data);
        ServiceData call = MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 1).getFirst();
        MultipartEncoder.Result textOnly = MultipartEncoder.encode("{\"evidenceType\":\"OTHER\"}", true, java.util.Map.of(), _ -> false);

        Assertions.assertThat(MultipartFuzzerTestSupport.mutate(call, textOnly)).isSameAs(textOnly);
    }

    @Test
    void shouldHaveToStringAndDescription() {
        Assertions.assertThat(fuzzer).hasToString(fuzzer.getClass().getSimpleName());
        Assertions.assertThat(fuzzer.description()).isNotBlank();
    }
}
