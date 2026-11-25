package com.endava.cats.report;

import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.Problem;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

@QuarkusTest
class ProblemMessageTest {
    @Test
    void shouldShowTheActualExampleWithoutChangingItsGroupingPattern() {
        CatsTestCaseSummary test = test("expected [405], actual [422]");
        Mockito.when(test.getResponseBody()).thenReturn("""
                {"errorCode":"XEL-4220","message":"'token' size must be between 14 and 36"}
                """);
        Problem problem = problem("XEL-4220: 'token' size must be between NUM and NUM");
        ProblemMessage message = ProblemMessage.of(problem, List.of(test));

        Assertions.assertThat(message.exampleText()).isEqualTo("XEL-4220: 'token' size must be between 14 and 36");
        Assertions.assertThat(problem.errorTemplate()).isEqualTo("XEL-4220: 'token' size must be between NUM and NUM");
        Assertions.assertThat(message.sampleTest()).isEqualTo(new ProblemCard.TestLink("Test7", "Test 7"));
    }

    @Test
    void shouldExtractTheActualHtmlMessageWithoutStylesOrNumberMasking() {
        CatsTestCaseSummary test = test("expected [405], actual [501]");
        Mockito.when(test.getResponseBody()).thenReturn("""
                <html><head><title>HTTP Status 501 – Not Implemented</title><style>h1 {color:red;}</style></head>
                <body><h1>HTTP Status 501 – Not Implemented</h1></body></html>
                """);

        Assertions.assertThat(ProblemMessage.of(problem("HTTP Status NUM – Not Implemented"), List.of(test)).exampleText())
                .isEqualTo("HTTP Status 501 – Not Implemented");
    }

    @Test
    void shouldDistinguishAnEmptyResponseFromAnUnavailableExample() {
        CatsTestCaseSummary test = test(null);
        Mockito.when(test.getResponseBody()).thenReturn("");

        Assertions.assertThat(ProblemMessage.of(problem("[Empty response body]"), List.of(test)).exampleText()).isEqualTo("[Empty response body]");
        Assertions.assertThat(ProblemMessage.of(problem("NUM"), List.of()).exampleText()).isEqualTo("Response body not available");
        Assertions.assertThat(ProblemMessage.of(problem("NUM"), List.of()).hasSampleTest()).isFalse();
        Mockito.when(test.getResponseBody()).thenReturn(null);
        Assertions.assertThat(ProblemMessage.of(problem("NUM"), List.of(test)).exampleText()).isEqualTo("Response body not available");
    }

    @Test
    void shouldBoundTheExampleWhileKeepingItsSourceTest() {
        CatsTestCaseSummary test = test(null);
        Mockito.when(test.getResponseBody()).thenReturn("Failure details " + "very long message ".repeat(30));
        ProblemMessage message = ProblemMessage.of(problem("Failure details <*>"), List.of(test));

        Assertions.assertThat(message.exampleText()).startsWith("Failure details ").endsWith("...").hasSizeLessThanOrEqualTo(160);
        Assertions.assertThat(message.hasSampleTest()).isTrue();
    }

    @Test
    void shouldPreferTheEffectiveRecordedCodesOverLegacyProse() {
        CatsTestCaseSummary test = test("expected [2XX], actual [422]");
        Mockito.when(test.hasRecordedExpectedResponse()).thenReturn(true);
        Mockito.when(test.getExpectedResponseCodes()).thenReturn(List.of("405"));

        Assertions.assertThat(ProblemMessage.of(problem("unsupported method"), List.of(test)).expectation())
                .isEqualTo("Expected HTTP 405; received HTTP 422.");
    }

    @Test
    void shouldNotRecoverCodesFromProseWhenAnUnknownTypedExpectationWasRecorded() {
        CatsTestCaseSummary test = test("expected [2XX], actual [422]");
        Mockito.when(test.hasRecordedExpectedResponse()).thenReturn(true);
        Mockito.when(test.getExpectedResponseCodes()).thenReturn(List.of());

        Assertions.assertThat(ProblemMessage.of(problem("validation failed"), List.of(test)).expectation()).isEqualTo("Received HTTP 422.");
    }
    @Test
    void shouldUseRecordedExpectationsAndLinkTheExampleTest() {
        CatsTestCaseSummary test2xx = test("expected [2XX], actual [422]");
        Mockito.when(test2xx.hasRecordedExpectedResponse()).thenReturn(true);
        Mockito.when(test2xx.getExpectedResponseCodes()).thenReturn(List.of("2XX"));
        ProblemMessage message = ProblemMessage.of(problem("validation failed"), List.of(test2xx));

        Assertions.assertThat(message.expectation()).isEqualTo("Expected HTTP 2xx; received HTTP 422.");
        Assertions.assertThat(message.sampleTest()).isEqualTo(new ProblemCard.TestLink("Test7", "Test 7"));

        CatsTestCaseSummary test400 = test("expected [422, 400], actual [422]");
        Mockito.when(test400.hasRecordedExpectedResponse()).thenReturn(true);
        Mockito.when(test400.getExpectedResponseCodes()).thenReturn(List.of("422", "400"));
        Assertions.assertThat(ProblemMessage.of(problem("invalid input"), List.of(test400)).expectation())
                .isEqualTo("Expected HTTP 400 or 422; received HTTP 422.");
    }

    @Test
    void shouldNotInventExpectationsWhenDetailsAreMissingOrDisagree() {
        Assertions.assertThat(ProblemMessage.of(problem("validation failed"), List.of(test(null))).expectation()).isEqualTo("Received HTTP 422.");
        CatsTestCaseSummary test2xx = test("expected [2XX], actual [422]");
        Mockito.when(test2xx.hasRecordedExpectedResponse()).thenReturn(true);
        Mockito.when(test2xx.getExpectedResponseCodes()).thenReturn(List.of("2XX"));
        CatsTestCaseSummary test400 = test("expected [400], actual [422]");
        Mockito.when(test400.hasRecordedExpectedResponse()).thenReturn(true);
        Mockito.when(test400.getExpectedResponseCodes()).thenReturn(List.of("400"));
        Assertions.assertThat(ProblemMessage.of(problem("validation failed"), List.of(test2xx, test400)).expectation())
                .isEqualTo("Received HTTP 422. See individual tests for their expected responses.");
        Assertions.assertThat(ProblemMessage.of(problem("validation failed"), List.of(
                        test2xx, test("No recorded expectation"))).expectation())
                .doesNotContain("Expected HTTP");
    }

    @Test
    void shouldExplainOnlyMarkersPresentInTheServerMessage() {
        Assertions.assertThat(ProblemMessage.of(problem("XEL: between NUM and NUM"), List.of()).tokens())
                .extracting(ProblemMessage.Token::text).containsExactly("NUM");
        Assertions.assertThat(ProblemMessage.of(problem("<FIELD>: <*> rejects <INPUT> (STR)"), List.of()).tokens())
                .extracting(ProblemMessage.Token::text).containsExactly("<*>", "<INPUT>", "<FIELD>", "STR");
        Assertions.assertThat(ProblemMessage.of(problem("ENUM and STRING are identifiers"), List.of()).hasTokens()).isFalse();
        Assertions.assertThat(ProblemMessage.of(problem("Operator GT requires a numeric value"), List.of()).hasTokens()).isFalse();
    }

    private static CatsTestCaseSummary test(String details) {
        CatsTestCaseSummary test = Mockito.mock(CatsTestCaseSummary.class);
        Mockito.when(test.getId()).thenReturn("Test 7");
        Mockito.when(test.getResultDetails()).thenReturn(details);
        return test;
    }

    private static Problem problem(String message) {
        return Problem.builder().errorTemplate(message).occurrences(List.of(
                new Problem.Occurrence("POST /items", List.of(), List.of("Test 7"), List.of(422), List.of("HappyPath")))).build();
    }
}
