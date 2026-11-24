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

import java.nio.charset.StandardCharsets;
import java.util.List;

@QuarkusTest
class MultipartHostileFileNamesFuzzerTest {
    private static final List<String> HOSTILE_NAMES = List.of(
            "../../../../etc/passwd.pdf",
            "..\\..\\..\\windows\\win.ini.pdf",
            "/etc/passwd.pdf",
            "C:\\Windows\\win.ini.pdf",
            "evidence\u0000.pdf",
            "evidence\r\nX-Injected: true.pdf",
            "a".repeat(300) + ".pdf",
            "...",
            "   ",
            "evidence\u202Efdp.exe",
            "evidence.pdf.exe");
    private static final List<String> SCENARIO_NAMES = List.of(
            "../../../../etc/passwd.pdf",
            "..\\..\\..\\windows\\win.ini.pdf",
            "/etc/passwd.pdf",
            "C:\\Windows\\win.ini.pdf",
            "evidence\\0.pdf",
            "evidence\\r\\nX-Injected: true.pdf",
            "a".repeat(37) + "...",
            "...",
            "   ",
            "evidence\\u202Efdp.exe",
            "evidence.pdf.exe");

    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private MultipartHostileFileNamesFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        fuzzer = new MultipartHostileFileNamesFuzzer(new SimpleExecutor(testCaseListener, serviceCaller));
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
    void shouldSendOneRequestPerHostileNameAndReportFourXxOrTwoXx() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartDataWithBinaryFile(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(200).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());

        fuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(11)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(response),
                Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX), Mockito.anyBoolean(), Mockito.eq(true));
        for (String name : SCENARIO_NAMES) {
            Mockito.verify(testCaseListener).addScenario(Mockito.any(),
                    Mockito.eq("Send a multipart request with the [file] file named [" + name + "]"), Mockito.any(Object[].class));
        }
        Assertions.assertThat(MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 11)).hasSize(11);
    }

    @Test
    void shouldWriteEachHostileNameRawWithoutChangingTheFileContent() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartDataWithBinaryFile(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
        fuzzer.fuzz(data);
        List<ServiceData> calls = MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 11);
        MultipartEncoder.Result encoded = MultipartFuzzerTestSupport.encoded();

        for (int i = 0; i < HOSTILE_NAMES.size(); i++) {
            MultipartEncoder.Result mutated = MultipartFuzzerTestSupport.mutate(calls.get(i), encoded);

            byte[] expectedHeader = ("name=\"file\"; filename=\"" + HOSTILE_NAMES.get(i)
                    + "\"\r\nContent-Type: application/octet-stream\r\n\r\nhello\r\n--" + encoded.boundary())
                    .getBytes(StandardCharsets.UTF_8);
            Assertions.assertThat(MultipartFuzzerTestSupport.contains(mutated.body(), expectedHeader))
                    .as("hostile name %d", i).isTrue();
            Assertions.assertThat(mutated.contentType()).isEqualTo(encoded.contentType());
        }
    }

    @Test
    void shouldLeaveTheBodyUnchangedWhenThereIsNoFilePart() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartDataWithBinaryFile(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
        fuzzer.fuzz(data);
        ServiceData call = MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 11).getFirst();
        MultipartEncoder.Result textOnly = MultipartEncoder.encode("{\"evidenceType\":\"OTHER\"}", true, java.util.Map.of(), _ -> false);

        Assertions.assertThat(MultipartFuzzerTestSupport.mutate(call, textOnly)).isSameAs(textOnly);
    }

    @Test
    void shouldHaveToStringAndDescription() {
        Assertions.assertThat(fuzzer).hasToString(fuzzer.getClass().getSimpleName());
        Assertions.assertThat(fuzzer.description()).isNotBlank();
    }
}
