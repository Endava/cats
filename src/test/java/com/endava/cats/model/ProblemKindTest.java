package com.endava.cats.model;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@QuarkusTest
class ProblemKindTest {

    @ParameterizedTest
    @EnumSource(CatsResultFactory.Reason.class)
    void shouldMapEveryReasonToAKindAndKeepServerErrorsFirst(CatsResultFactory.Reason reason) {
        Assertions.assertThat(ProblemKind.of(200, reason.value())).isNotNull();
        Assertions.assertThat(ProblemKind.of(422, reason.value())).isNotNull();
        Assertions.assertThat(ProblemKind.of(999, reason.value())).isNotNull();
        Assertions.assertThat(ProblemKind.of(500, reason.value())).isEqualTo(ProblemKind.SERVER_ERROR);
        Assertions.assertThat(ProblemKind.of(503, reason.description())).isEqualTo(ProblemKind.SERVER_ERROR);
        Assertions.assertThat(CatsResultFactory.Reason.fromResultReason(reason.value())).contains(reason);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "422|Unexpected response code: 422|ERROR_MESSAGE",
            "400|Unexpected behaviour 400|ERROR_MESSAGE",
            "404|Not found|ERROR_MESSAGE",
            "200|Unexpected response code: 200|ACCEPTED_REQUEST",
            "302|Unexpected behaviour 302|ACCEPTED_REQUEST",
            "201|XSS payload reflected in response|ACCEPTED_REQUEST",
            "201|Not matching response schema|RESPONSE_VIOLATION",
            "422|Not matching response schema|RESPONSE_VIOLATION",
            "400|Response content type not matching the contract|RESPONSE_VIOLATION",
            "200|Missing recommended security headers|RESPONSE_VIOLATION",
            "200|Error details leak|RESPONSE_VIOLATION",
            "422|Undocumented response code: 422|STATUS_CODE",
            "405|Missing Allowed header|STATUS_CODE",
            "405|You forgot to implement this functionality!|STATUS_CODE",
            "953|Connection refused|NO_RESPONSE",
            "0|Missing descriptions for operations|NO_RESPONSE",
            "999|Unexpected response code: 999|NO_RESPONSE",
            "501|Not implemented|SERVER_ERROR",
            "500|Missing recommended security headers|SERVER_ERROR"
    })
    void shouldDecideTheKindByStatusThenVerdictThenStatusClass(int status, String resultReason, ProblemKind expected) {
        Assertions.assertThat(ProblemKind.of(status, resultReason)).isEqualTo(expected);
    }

    @Test
    void shouldResolveReasonsWithAStatusSuffixByTheLongestPrefix() {
        Assertions.assertThat(CatsResultFactory.Reason.fromResultReason("Undocumented response code: 422"))
                .contains(CatsResultFactory.Reason.UNDOCUMENTED_RESPONSE_CODE);
        Assertions.assertThat(CatsResultFactory.Reason.fromResultReason("Unexpected behaviour 400"))
                .contains(CatsResultFactory.Reason.UNEXPECTED_BEHAVIOUR);
        Assertions.assertThat(CatsResultFactory.Reason.fromResultReason("Possible command injection vulnerability"))
                .contains(CatsResultFactory.Reason.POSSIBLE_COMMAND_INJECTION);
        Assertions.assertThat(CatsResultFactory.Reason.fromResultReason(null)).isEmpty();
        Assertions.assertThat(CatsResultFactory.Reason.fromResultReason("A linter finding")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"SERVER_ERROR,true", "ERROR_MESSAGE,true", "RESPONSE_VIOLATION,false", "STATUS_CODE,false",
            "ACCEPTED_REQUEST,false", "NO_RESPONSE,false"})
    void shouldOnlyIdentifyMessageKindsByTheServerMessage(ProblemKind kind, boolean expected) {
        Assertions.assertThat(kind.isIdentifiedByServerMessage()).isEqualTo(expected);
    }
}
