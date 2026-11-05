package com.endava.cats.command.triage;

import java.util.List;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Status codes and families ({@code 2XX}, {@code 404}, ...) parsed from a test case's {@code expectedResult} text
 * such as {@code "Should return [4XX|2XX]"} or {@code "Should return [200, 201]"}.
 */
record ExpectedCodes(List<String> tokens) {
    private static final Pattern CODE_TOKEN = Pattern.compile("\\b([1-5](?:XX|\\d{2}))\\b", Pattern.CASE_INSENSITIVE);

    static ExpectedCodes parse(String expectedResult) {
        if (expectedResult == null) {
            return new ExpectedCodes(List.of());
        }
        return new ExpectedCodes(CODE_TOKEN.matcher(expectedResult).results()
                .map(MatchResult::group)
                .map(token -> token.toUpperCase(Locale.ROOT))
                .distinct()
                .toList());
    }

    boolean expectsOnly2xx() {
        return expectsOnlyFamily('2');
    }

    boolean expectsOnly4xx() {
        return expectsOnlyFamily('4');
    }

    boolean expects2xx() {
        return tokens.stream().anyMatch(token -> token.charAt(0) == '2');
    }

    boolean allows(int statusCode) {
        String code = String.valueOf(statusCode);
        return tokens.stream().anyMatch(token -> matches(token, code));
    }

    boolean isEmpty() {
        return tokens.isEmpty();
    }

    private boolean expectsOnlyFamily(char family) {
        return !tokens.isEmpty() && tokens.stream().allMatch(token -> token.charAt(0) == family);
    }

    private static boolean matches(String token, String code) {
        return token.endsWith("XX") ? token.charAt(0) == code.charAt(0) : token.equals(code);
    }
}
