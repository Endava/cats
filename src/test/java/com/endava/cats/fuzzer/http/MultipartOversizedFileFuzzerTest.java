package com.endava.cats.fuzzer.http;

import com.endava.cats.context.CatsGlobalContext;
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

@QuarkusTest
class MultipartOversizedFileFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private CatsGlobalContext globalContext;
    private MultipartOversizedFileFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        globalContext = new CatsGlobalContext();
        fuzzer = new MultipartOversizedFileFuzzer(new SimpleExecutor(testCaseListener, serviceCaller), globalContext);
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
    void shouldSendOneRequestPerFilePartWithTheDefaultSizeAndReportFourXx() {
        FuzzingData data = MultipartFuzzerTestSupport.multipartDataWithBinaryFile(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        CatsResponse response = CatsResponse.builder().body("{}").responseCode(422).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());

        fuzzer.fuzz(data);

        Mockito.verify(testCaseListener).addScenario(Mockito.any(),
                Mockito.eq("Send a multipart request with the [file] file of [10485761] bytes"), Mockito.any(Object[].class));
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(response),
                Mockito.eq(ResponseCodeFamilyPredefined.FOURXX), Mockito.anyBoolean(), Mockito.eq(true));
        MultipartEncoder.Result encoded = MultipartFuzzerTestSupport.encoded();
        ServiceData call = MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 1).getFirst();
        MultipartEncoder.Result mutated = MultipartFuzzerTestSupport.mutate(call, encoded);
        Assertions.assertThat(mutated.body()).hasSize(encoded.body().length - 5 + 10485761);
        Assertions.assertThat(MultipartFuzzerTestSupport.contains(mutated.body(), "\r\n\r\nhello\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"2048,2048", "abc,10485761", "0,10485761", "-5,10485761", "'',10485761"})
    void shouldUseTheConfiguredSizeOrFallBackToTheDefault(String configured, int expectedSize) {
        globalContext.getFuzzersConfiguration().setProperty("MultipartOversizedFile.sizeBytes", configured);
        FuzzingData data = MultipartFuzzerTestSupport.multipartDataWithBinaryFile(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());

        fuzzer.fuzz(data);

        MultipartEncoder.Result encoded = MultipartFuzzerTestSupport.encoded();
        ServiceData call = MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 1).getFirst();
        Assertions.assertThat(MultipartFuzzerTestSupport.mutate(call, encoded).body())
                .hasSize(encoded.body().length - 5 + expectedSize);
    }

    @Test
    void shouldCutTheOriginalContentWhenTheConfiguredSizeIsSmaller() {
        globalContext.getFuzzersConfiguration().setProperty("MultipartOversizedFile.sizeBytes", "3");
        FuzzingData data = MultipartFuzzerTestSupport.multipartDataWithBinaryFile(HttpMethod.POST, MultipartFuzzerTestSupport.PAYLOAD);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());

        fuzzer.fuzz(data);

        ServiceData call = MultipartFuzzerTestSupport.serviceCalls(serviceCaller, 1).getFirst();
        String body = MultipartFuzzerTestSupport.text(MultipartFuzzerTestSupport.mutate(call, MultipartFuzzerTestSupport.encoded()).body());
        Assertions.assertThat(body).contains("\r\n\r\nhel\r\n--").doesNotContain("hello");
    }

    @Test
    void shouldHaveToStringAndDescription() {
        Assertions.assertThat(fuzzer).hasToString(fuzzer.getClass().getSimpleName());
        Assertions.assertThat(fuzzer.description()).contains("MultipartOversizedFile.sizeBytes");
    }
}
