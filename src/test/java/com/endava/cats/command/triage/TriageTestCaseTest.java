package com.endava.cats.command.triage;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonNull;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class TriageTestCaseTest {
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalid", "[]", "[1]", "null", "3"})
    void shouldRejectNonObjectPayloads(String payload) {
        assertThat(TriageFixtures.rejected("Fuzzer", "2XX", "/items", payload, 400, "error").payloadRootObject()).isEmpty();
    }

    @Test
    void shouldExtractFirstObjectFromRootArray() {
        TriageTestCase test = TriageFixtures.rejected("Fuzzer", "2XX", "/items", " [{\"id\":1}]", 400, "error");
        assertThat(test.payloadRootObject()).contains(JsonUtils.parseAsJsonElement("{\"id\":1}").getAsJsonObject());
        assertThat(test.hasRootArrayPayload()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"invalid\"", "\"\\\"text\\\"\"", "{}", "[]", "\"{\\\"id\\\":1}\""})
    void shouldReadPersistedResponseBodies(String jsonBody) {
        TriageTestCase test = TriageFixtures.testCase(1, "{\"request\":{},\"response\":{\"jsonBody\":" + jsonBody + "}}");
        boolean structured = jsonBody.equals("{}") || jsonBody.equals("[]") || jsonBody.startsWith("\"{");
        assertThat(test.responseJson().isPresent()).isEqualTo(structured);
        if (structured) {
            assertThat(test.rawResponseBody()).containsAnyOf("{", "[");
        }
    }

    @Test
    void shouldUseFallbackMetadataAndMatchOnlyPathMutation() {
        TriageTestCase test = TriageFixtures.testCase(7, """
                {"path":"/fallback", "fullRequestPath":"/full", "result":"WARNING",
                 "request":{}, "response":{}, "requestProvenance":null}
                """);
        assertThat(test.method()).isEmpty();
        assertThat(test.path()).isEqualTo("/fallback");
        assertThat(test.url()).isEqualTo("/full");
        assertThat(test.isWarning()).isTrue();
        assertThat(test.isError()).isFalse();
        assertThat(test.mutationTargets()).isEmpty();
        assertThat(test.runtimeCorrelations()).isEmpty();
        TriageTestCase empty = TriageFixtures.testCase(1, "{\"request\":{},\"response\":{}}");
        empty.testCase().setPath(null);
        assertThat(empty.path()).isEmpty();
        assertThat(empty.url()).isEmpty();
        empty.testCase().getRequestProvenance().addMutationTargets(List.of(RequestTarget.body("id"), RequestTarget.path("other"), RequestTarget.path("id")));
        assertThat(empty.isPathMutated("id")).isTrue();
        assertThat(empty.isPathMutated("missing")).isFalse();
    }
    @ParameterizedTest
    @CsvSource({"warn,true,false", "warning,true,false", "error,false,true", "success,false,false"})
    void shouldRecognizeResultLevels(String result, boolean warning, boolean error) {
        TriageTestCase test = TriageFixtures.testCase(1, "{\"result\":\"" + result + "\",\"request\":{},\"response\":{}}");
        assertThat(test.isWarning()).isEqualTo(warning);
        assertThat(test.isError()).isEqualTo(error);
    }

    @ParameterizedTest
    @MethodSource("rawResponses")
    void shouldHandleMissingAndQuotedRawResponseBodies(CatsResponse response, String expectedRaw, boolean inspectable) {
        TriageTestCase test = TriageFixtures.testCase(1, "{\"request\":{},\"response\":{}}");
        test.testCase().setResponse(response);
        assertThat(test.rawResponseBody()).isEqualTo(expectedRaw);
        assertThat(test.responseJson().isPresent()).isEqualTo(inspectable);
    }

    static Stream<Arguments> rawResponses() {
        return Stream.of(
                Arguments.of(CatsResponse.builder().build(), null, false),
                Arguments.of(CatsResponse.builder().jsonBody(JsonNull.INSTANCE).build(), null, false),
                Arguments.of(CatsResponse.builder().body("\"text\"").build(), "\"text\"", false),
                Arguments.of(CatsResponse.builder().body("{}").build(), "{}", true));
    }

}
