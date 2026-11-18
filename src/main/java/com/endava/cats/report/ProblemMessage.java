package com.endava.cats.report;

import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.Problem;
import com.endava.cats.report.error.extract.ErrorBodyExtraction;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Presentation details of a problem identified by its server message.
 *
 * @param expectation the received status and, when recorded consistently, the expected responses
 * @param tokens only the normalization markers present in the message
 * @param sampleTest the test whose response is shown, or {@code null} without an available test
 * @param example a bounded, unmasked excerpt of that test's server message, or {@code null} when unavailable
 */
public record ProblemMessage(String expectation, List<Token> tokens, ProblemCard.TestLink sampleTest, String example) {
    // Exclude opening brackets too: malformed outer prose must not hide a valid nested declaration.
    private static final Pattern EXPECTED = Pattern.compile("expected \\[([^\\[\\]]*+)\\], actual \\[\\d{3}\\]");
    private static final Pattern LEGACY_HTTP_CODE = Pattern.compile("[1-5](?:\\d{2}|[Xx]{2})");
    private static final List<Token> TOKEN_LEGEND = List.of(
            new Token("<*>", "A value generalized across matching messages"),
            new Token("<INPUT>", "A value CATS sent in the request"),
            new Token("<FIELD>", "A field name"),
            new Token("NUM", "A number hidden for grouping; see the example response for its value"),
            new Token("STR", "A string hidden for grouping; see the example response for its value"));

    static ProblemMessage of(Problem problem, List<CatsTestCaseSummary> tests) {
        var sample = tests.stream().findFirst().map(test -> new ProblemCard.TestLink(test.getId().replace(" ", ""), test.getId())).orElse(null);
        String example = tests.stream().findFirst().map(CatsTestCaseSummary::getResponseBody)
                .map(body -> ErrorBodyExtraction.rootCauseSummary(body, null)).orElse(null);
        return new ProblemMessage(expectation(problem, tests), TOKEN_LEGEND.stream().filter(token -> token.presentIn(problem.errorTemplate())).toList(), sample, example);
    }

    public boolean hasTokens() {
        return !tokens.isEmpty();
    }

    public boolean hasSampleTest() {
        return sampleTest != null;
    }

    public String exampleText() {
        return Optional.ofNullable(example).orElse("Response body not available");
    }

    private static String expectation(Problem problem, List<CatsTestCaseSummary> tests) {
        String received = problem.occurrences().stream().flatMap(occurrence -> occurrence.statuses().stream())
                .map(String::valueOf).distinct().collect(Collectors.joining(" or "));
        List<String> expected = tests.stream().map(ProblemMessage::expected).distinct().toList();
        if (expected.size() == 1 && !expected.getFirst().isEmpty()) {
            return "Expected HTTP %s; received HTTP %s.".formatted(expected.getFirst(), received);
        }
        return "Received HTTP %s.%s".formatted(received,
                expected.size() > 1 ? " See individual tests for their expected responses." : "");
    }

    private static String expected(CatsTestCaseSummary test) {
        if (test.hasRecordedExpectedResponse()) {
            return test.getExpectedResponseCodes().stream().map(code -> code.replace('X', 'x')).distinct().sorted().collect(Collectors.joining(" or "));
        }
        return EXPECTED.matcher(StringUtils.defaultString(test.getResultDetails())).results()
                .map(match -> List.of(match.group(1).split(", *", -1)))
                .filter(codes -> codes.stream().allMatch(code -> LEGACY_HTTP_CODE.matcher(code).matches())).findFirst()
                .map(codes -> codes.stream().map(code -> code.replace('X', 'x'))
                        .distinct().sorted().collect(Collectors.joining(" or "))).orElse("");
    }

    /**
     * A normalization marker and its explanation.
     *
     * @param text the marker as shown in the message
     * @param explanation what it represents
     */
    public record Token(String text, String explanation) {
        private boolean presentIn(String message) {
            return message != null && (text.startsWith("<") ? message.contains(text)
                    : Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(text) + "(?![\\p{L}\\p{N}_])").matcher(message).find());
        }
    }
}
