package com.endava.cats.report.error.fields;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

@QuarkusTest
class ErrorFieldRoleResolverTest {

    @ParameterizedTest
    @CsvSource({
            "code,TYPE", "errorCode,TYPE", "error_code,TYPE", "ERROR_CODE,TYPE", "reason-code,TYPE", "HTTPCode,TYPE", "statusCode,TYPE",
            "type,TYPE", "errorType,TYPE", "error_kind,TYPE", "codes,TYPE",
            "message,MESSAGE", "errorMessage,MESSAGE", "error_description,MESSAGE", "ErrorDescription,MESSAGE", "msg,MESSAGE",
            "error-msg,MESSAGE", "detail,MESSAGE", "details,MESSAGE", "reason,MESSAGE", "humanText,MESSAGE", "descriptions,MESSAGE",
            "faultstring,MESSAGE", "melding,MESSAGE", "frontendFeilmelding,MESSAGE", "Fehlermeldung,MESSAGE", "errormessage,MESSAGE"
    })
    void shouldAssignTheRoleOfTheLastWordWhateverTheSpelling(String field, ErrorFieldRole role) {
        Assertions.assertThat(ErrorFieldRoleResolver.BY_NAME.roleOf(field)).contains(role);
    }

    @ParameterizedTest
    @CsvSource({"context", "barcode", "decode", "timestamp", "path", "status", "title", "error", "requestId", "errors", "trace", "codeName"})
    void shouldNotGuessARoleForOtherFields(String field) {
        Assertions.assertThat(ErrorFieldRoleResolver.BY_NAME.roleOf(field)).isEqualTo(Optional.empty());
    }

    @ParameterizedTest
    @CsvSource({"statusCode,true", "status_code,true", "httpCode,true", "HTTPCode,true", "responseCode,true", "errorCode,false", "code,false",
            "reasonCode,false"})
    void shouldRecognizeTheCodesOfTheTransport(String field, boolean transport) {
        Assertions.assertThat(ErrorFieldRoleResolver.isTransportCode(field)).isEqualTo(transport);
    }

    @ParameterizedTest
    @CsvSource({"errorCode,code", "error_description,description", "reason-code,code", "HTTPCode,code", "Fehlermeldung,fehlermeldung", "code,code"})
    void shouldTakeTheLastWordInLowerCase(String field, String lastWord) {
        Assertions.assertThat(ErrorFieldRoleResolver.lastWord(field)).isEqualTo(lastWord);
    }

    @ParameterizedTest
    @CsvSource({"message,MESSAGE,true", "message,TYPE,false", "code,TYPE,true", "timestamp,TYPE,false"})
    void shouldTestRolesThroughTheInterface(String field, ErrorFieldRole role, boolean expected) {
        Assertions.assertThat(ErrorFieldRoleResolver.BY_NAME.has(field, role)).isEqualTo(expected);
    }
}
