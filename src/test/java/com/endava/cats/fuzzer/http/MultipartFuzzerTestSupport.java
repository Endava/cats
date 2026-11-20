package com.endava.cats.fuzzer.http;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.io.util.MultipartEncoder;
import com.endava.cats.model.FuzzingData;
import io.swagger.v3.oas.models.media.Schema;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MultipartFuzzerTestSupport {
    static final String PAYLOAD = "{\"file\":\"aGVsbG8=\",\"evidenceType\":\"OTHER\",\"checksum\":\"abc\"}";

    private MultipartFuzzerTestSupport() {
    }

    static FuzzingData multipartData(HttpMethod method, String payload) {
        FuzzingData data = FuzzingData.builder().method(method).path("/uploads").reqSchema(new Schema<>())
                .requestContentTypes(List.of("multipart/form-data")).responseCodes(Set.of("400")).build();
        ReflectionTestUtils.setField(data, "processedPayload", payload);
        return data;
    }

    static FuzzingData multipartDataWithBinaryFile(HttpMethod method, String payload) {
        FuzzingData data = FuzzingData.builder().method(method).path("/uploads").reqSchema(new Schema<>())
                .requestContentTypes(List.of("multipart/form-data")).responseCodes(Set.of("400"))
                .requestPropertyTypes(Map.of("file", new Schema<>().type("string").format("binary"))).build();
        ReflectionTestUtils.setField(data, "processedPayload", payload);
        return data;
    }

    static boolean contains(byte[] source, byte[] target) {
        for (int i = 0; i <= source.length - target.length; i++) {
            if (Arrays.equals(source, i, i + target.length, target, 0, target.length)) {
                return true;
            }
        }
        return false;
    }

    static MultipartEncoder.Result encoded() {
        return MultipartEncoder.encode(PAYLOAD, true, Map.of("file", new Schema<>().type("string").format("binary")), _ -> false);
    }

    static List<ServiceData> serviceCalls(ServiceCaller serviceCaller, int expectedCalls) {
        ArgumentCaptor<ServiceData> captor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller, Mockito.times(expectedCalls)).call(captor.capture());
        return captor.getAllValues();
    }

    static MultipartEncoder.Result mutate(ServiceData call, MultipartEncoder.Result encoded) {
        return call.getMultipartMutation().apply(encoded);
    }

    static String text(byte[] body) {
        return new String(body, StandardCharsets.ISO_8859_1);
    }

    static int count(String text, String token) {
        int count = 0;
        int index = text.indexOf(token);
        while (index >= 0) {
            count++;
            index = text.indexOf(token, index + token.length());
        }
        return count;
    }
}
