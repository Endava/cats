package com.endava.cats.report.error.mask;

import com.endava.cats.model.ErrorSignature;
import com.endava.cats.model.RequestVocabulary;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

@QuarkusTest
class CustomErrorEnvelopeTest {

    static Stream<Arguments> envelopes() {
        return Stream.of(
                Arguments.of("{\"errorCode\":\"USR-001\",\"message\":\"User not found\"}", "USR-001", "User not found"),
                Arguments.of("{\"errorCode\":\"err-01\",\"message\":\"User not found\"}", "err-01", "User not found"),
                Arguments.of("{\"code\":\"ERR:12\",\"message\":\"Boom\"}", "ERR:12", "Boom"),
                Arguments.of("{\"code\":\"E100\",\"message\":\"Validation failed\",\"description\":\"name is too long\"}",
                        "E100", "Validation failed; name is too long"),
                Arguments.of("{\"code\":\"E100\",\"message\":\"Validation failed\",\"description\":{\"field\":\"name\",\"reason\":\"too long\"}}",
                        "E100", "Validation failed; name: too long"),
                Arguments.of("{\"errorCode\":\"E7\",\"errorMessage\":\"Disk full\",\"errorDescription\":\"volume 3 has no space\",\"httpCode\":507}",
                        "E7", "Disk full; volume NUM has no space"),
                Arguments.of("{\"error\":{\"code\":\"E9\",\"message\":\"Quota exceeded for tenant 7\"}}", "E9", "Quota exceeded for tenant NUM"),
                Arguments.of("{\"errors\":[{\"code\":\"E1\",\"description\":\"name too long\"},{\"code\":\"E2\",\"description\":\"email invalid\"}]}",
                        "", "E1: name too long; E2: email invalid"),
                Arguments.of("{\"codes\":[\"E1\",\"E2\"],\"descriptions\":[\"name too long\",\"email invalid\"]}",
                        "", "E1: name too long; E2: email invalid"),
                Arguments.of("{\"failure\":{\"reasonCode\":\"AUTH-17\",\"humanText\":\"Token expired for user 42\"}}",
                        "AUTH-17", "Token expired for user NUM"),
                Arguments.of("{\"melding\":\"Ugyldig verdi\",\"frontendFeilmelding\":\"Navn er for langt\"}",
                        "", "Navn er for langt; Ugyldig verdi")
        );
    }

    @ParameterizedTest
    @MethodSource("envelopes")
    void shouldTellCustomEnvelopesApart(String body, String errorType, String tokens) {
        ErrorSignature signature = ErrorSignatureMasker.signatureOf(body, null, RequestVocabulary.EMPTY);

        Assertions.assertThat(signature.errorType()).isEqualTo(errorType);
        Assertions.assertThat(String.join(" ", signature.tokens())).isEqualTo(tokens);
    }

    @Test
    void shouldStillMaskPerInstanceCodes() {
        ErrorSignature first = ErrorSignatureMasker.signatureOf("{\"code\":\"ORD-83749213\",\"message\":\"Boom\"}", null, RequestVocabulary.EMPTY);
        ErrorSignature second = ErrorSignatureMasker.signatureOf("{\"code\":\"ORD-12345678\",\"message\":\"Boom\"}", null, RequestVocabulary.EMPTY);

        Assertions.assertThat(first).isEqualTo(second);
        Assertions.assertThat(first.errorType()).doesNotContain("8374");
    }
}
