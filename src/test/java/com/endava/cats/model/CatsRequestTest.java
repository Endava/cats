package com.endava.cats.model;

import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.List;

@QuarkusTest
class CatsRequestTest {

    @Test
    void shouldExpandMaskedPlaceholdersFromTheEnvironmentInCurlCommands() {
        CatsRequest request = CatsRequest.builder()
                .httpMethod("GET")
                .url("https://api.example.com/items?token=$$TOKEN")
                .headers(List.of(new KeyValuePair<>("Authorization", "$$Authorization"), new KeyValuePair<>("X-Note", "it's $5")))
                .build();

        Assertions.assertThat(request.toCurlCommand()).isEqualTo(
                "curl -i -X GET -H 'Authorization: '\"${Authorization}\"'' -H 'X-Note: it'\\''s $5' 'https://api.example.com/items?token='\"${TOKEN}\"''");
    }

    @Test
    void shouldReturnEmptyCurlForPlaceholderRequests() {
        Assertions.assertThat(CatsRequest.empty().toCurlCommand()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"N/A", "####", "UNKNOWN"})
    void shouldReturnEmptyCurlForRequestsWithoutAnHttpMethod(String method) {
        CatsRequest linterRequest = CatsRequest.builder().httpMethod(method).url("####").payload("{}").build();

        Assertions.assertThat(linterRequest.toCurlCommand()).isEmpty();
    }

    @Test
    void shouldSendBodyForBodyMethodsRegardlessOfCase() {
        CatsRequest request = CatsRequest.builder().httpMethod("post").url("http://localhost/pets").payload("{\"a\":1}").build();

        Assertions.assertThat(request.toCurlCommand()).isEqualTo("curl -i -X post -d '{\"a\":1}' 'http://localhost/pets'");
    }

    @Test
    void shouldRenderBinaryBodyCurlWithDataBinary() {
        CatsRequest request = CatsRequest.builder()
                .httpMethod("POST")
                .url("http://localhost/upload")
                .headers(List.of(new KeyValuePair<>("Content-Type", "multipart/form-data; boundary=x")))
                .payload("{\"file\":\"<file name=a.bin size=3 sha256=00>\"}")
                .encodedBody(Base64.getEncoder().encodeToString(new byte[]{0x00, 0x01, (byte) 0xFF}))
                .build();

        Assertions.assertThat(request.toCurlCommand()).isEqualTo(
                "printf '%s' 'AAH/' | base64 --decode | curl -i -X POST -H 'Content-Type: multipart/form-data; boundary=x' --data-binary @- 'http://localhost/upload'");
    }

    @Test
    void shouldRenderTextBodyCurlUnchanged() {
        CatsRequest request = CatsRequest.builder()
                .httpMethod("POST")
                .url("http://localhost/pets")
                .headers(List.of(new KeyValuePair<>("Content-Type", "application/json")))
                .payload("{\"a\":1}")
                .build();

        Assertions.assertThat(request.toCurlCommand()).isEqualTo(
                "curl -i -X POST -H 'Content-Type: application/json' -d '{\"a\":1}' 'http://localhost/pets'");
    }

    @Test
    void shouldNotRenderBinaryBodyForRequestsWithoutBody() {
        CatsRequest request = CatsRequest.builder()
                .httpMethod("GET")
                .url("http://localhost/pets")
                .encodedBody("AAH/")
                .build();

        Assertions.assertThat(request.toCurlCommand()).isEqualTo("curl -i -X GET 'http://localhost/pets'");
    }
}
