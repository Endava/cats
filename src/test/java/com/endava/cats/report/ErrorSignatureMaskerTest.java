package com.endava.cats.report;

import com.endava.cats.model.RequestVocabulary;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

@QuarkusTest
class ErrorSignatureMaskerTest {

    static Stream<Arguments> maskedDetails() {
        return Stream.of(
                Arguments.of("{\"title\":\"Bad Request\",\"detail\":\"name: must not be blank\"}", "{\"name\":\"\"}", null, null,
                        "<FIELD>: must not be blank"),
                Arguments.of("{\"message\":\"Value \\\"hello world\\\" is invalid\"}", "{\"note\":\"hello world\"}", null, null,
                        "Value \"<INPUT>\" is invalid"),
                Arguments.of("{\"message\":\"Unable to determine status\"}", "{\"to\":\"0xabc\"}", null, null,
                        "Unable to determine status"),
                Arguments.of("{\"message\":\"For input string: \\\"a14f5\\\" under radix 16\"}", null,
                        "http://localhost/accounts/0xa14f5/balance", "/accounts/{id}/balance",
                        "For input string: \"<INPUT>\" under radix NUM"),
                Arguments.of("{\"message\":\"Cannot invoke \\\"String.length()\\\" because \\\"val\\\" is null\"}", null, null, null,
                        "Cannot invoke \"String.length()\" because \"val\" is null"),
                Arguments.of("{\"message\":\"age must be less than 100 but was 12345\"}", "{\"age\":12345}", null, null,
                        "<FIELD> must be less than NUM but was NUM"),
                Arguments.of("{\"message\":\"not one of [A, B, C]\"}", null, null, null, "not one of [LIST]")
        );
    }

    @ParameterizedTest
    @MethodSource("maskedDetails")
    void shouldMaskEchoedRequestDataAndVariableParts(String body, String payload, String url, String contractPath, String expected) {
        ErrorSignature signature = ErrorSignatureMasker.signatureOf(body, null, RequestVocabulary.from(payload, url, contractPath));

        Assertions.assertThat(String.join(" ", signature.tokens())).isEqualTo(expected);
    }

    @Test
    void shouldRecoverTypeAndDetailFromTruncatedJson() {
        String truncated = "{\"timestamp\":\"2026-11-10T18:58:25.912+0000\",\"status\":500,\"error\":\"Internal Server Error\","
                + "\"message\":\"Unable to determine sync status of node\",\"path\":\"/0x96a5c91cc5200ba51fc15439245d2ffa1caa83";

        ErrorSignature signature = ErrorSignatureMasker.signatureOf(truncated, null, RequestVocabulary.EMPTY);

        Assertions.assertThat(signature.errorType()).isEqualTo("Internal Server Error");
        Assertions.assertThat(String.join(" ", signature.tokens())).isEqualTo("Unable to determine sync status of node");
    }

    @Test
    void shouldKeepErrorTypeAndStackFrameOutOfTheTokens() {
        String body = "{\"title\":\"NullPointerException\",\"detail\":\"boom\",\"trace\":\"at com.acme.OrderService.create(OrderService.java:88)\"}";

        ErrorSignature signature = ErrorSignatureMasker.signatureOf(body, null, RequestVocabulary.EMPTY);

        Assertions.assertThat(signature).isEqualTo(new ErrorSignature("NullPointerException", java.util.List.of("boom"),
                "com.acme.OrderService.create(OrderService.java:88)"));
    }

    @Test
    void shouldMaskVariablePartsOfErrorTypesButKeepNumericCodes() {
        Assertions.assertThat(ErrorSignatureMasker.signatureOf("{\"error\":\"User 123 not found\"}", null, RequestVocabulary.EMPTY).errorType())
                .isEqualTo("User NUM not found");
        Assertions.assertThat(ErrorSignatureMasker.signatureOf("{\"code\":\"404\",\"message\":\"missing\"}", null, RequestVocabulary.EMPTY).errorType())
                .isEqualTo("404");
    }

    @Test
    void shouldUseFallbackDetailsOnlyForEmptyBodies() {
        Assertions.assertThat(ErrorSignatureMasker.signatureOf("", null, RequestVocabulary.EMPTY))
                .isEqualTo(new ErrorSignature(ErrorSignatureMasker.EMPTY_TYPE, java.util.List.of(), ""));
        Assertions.assertThat(ErrorSignatureMasker.signatureOf("[Empty response body]", "Unexpected code 500", RequestVocabulary.EMPTY).tokens())
                .containsExactly("Unexpected", "code", "NUM");
    }
}
