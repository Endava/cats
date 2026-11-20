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
class MultipartDuplicatePartsFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private MultipartDuplicatePartsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        fuzzer = new MultipartDuplicatePartsFuzzer(new SimpleExecutor(testCaseListener, serviceCaller));
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
    void shouldSendOneRequestPerTopLevelPartAndReportFourXx() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartData(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(422).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());

        fuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(Mockito.any(), Mockito.eq(data),
                Mockito.eq(response), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.eq(true));
        Assertions.assertThat(MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 3)).hasSize(3);
    }

    @Test
    void shouldDuplicateOnlyTheTargetedPart() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartData(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());
        fuzzer.fuzz(data);
        List<ServiceData> calls = MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 3);
        MultipartEncoder.Result encoded = MultipartFuzzerTestSupport.encoded();
        List<String> names = List.of("file", "evidenceType", "checksum");

        for (int i = 0; i < names.size(); i++) {
            MultipartEncoder.Result mutated = MultipartFuzzerTestSupport.mutate(calls.get(i), encoded);
            String body = MultipartFuzzerTestSupport.text(mutated.body());

            Assertions.assertThat(mutated.contentType()).isEqualTo(encoded.contentType());
            for (String name : names) {
                Assertions.assertThat(MultipartFuzzerTestSupport.count(body, "name=\"" + name + "\""))
                        .isEqualTo(name.equals(names.get(i)) ? 2 : 1);
            }
            Assertions.assertThat(body).endsWith("--" + encoded.boundary() + "--\r\n");
        }
    }

    @Test
    void shouldHaveToStringAndDescription() {
        Assertions.assertThat(fuzzer).hasToString(fuzzer.getClass().getSimpleName());
        Assertions.assertThat(fuzzer.description()).isNotBlank();
    }
}
