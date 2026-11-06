package com.endava.cats.command.triage;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a {@code 400}/{@code 422} error message demands from the request. Only messages expressing presence,
 * absence or uniqueness can be satisfied with a {@code --correlations} rule; value-format rejections
 * ({@code "'HYI' is not a valid sort direction"}) yield no intent.
 */
enum FailureIntent {
    UNIQUE(List.of(
            Pattern.compile("\\balready\\s+(?:exists|in\\s+use|taken|registered)\\b"),
            Pattern.compile("\\bduplicate\\b"),
            Pattern.compile("\\b(?:must\\s+be|not)\\s+unique\\b"))),
    REMOVE(List.of(
            Pattern.compile("\\bmust\\s+be\\s+(?:null|empty|absent|omitted)\\b"),
            Pattern.compile("\\b(?:must|should)\\s+not\\s+be\\s+(?:present|provided|set|specified|sent|supplied)\\b"),
            Pattern.compile("\\bcannot\\s+be\\s+(?:set|provided|specified|supplied)\\b"),
            Pattern.compile("\\bnot\\s+(?:allowed|permitted)\\b"),
            Pattern.compile("\\bforbidden\\b"),
            Pattern.compile("\\b(?:unrecognized|unknown|unexpected)\\s+(?:field|property|attribute)\\b"))),
    REQUIRE(List.of(
            Pattern.compile("\\b(?:is|are)\\s+(?:required|mandatory|missing)\\b"),
            Pattern.compile("\\b(?:must|should)\\s+not\\s+be\\s+(?:null|blank|empty)\\b"),
            Pattern.compile("\\bcannot\\s+be\\s+(?:null|blank|empty)\\b"),
            Pattern.compile("\\bmust\\s+be\\s+(?:provided|specified|present|set)\\b"),
            Pattern.compile("\\bmust\\s+(?:have|contain)\\b"),
            Pattern.compile("\\brequires?\\b"),
            Pattern.compile("\\bmissing\\b")));

    private static final Pattern NULL_NOT_ALLOWED = Pattern.compile("\\b(?:null|empty|blank)\\s+(?:values?\\s+)?(?:is\\s+|are\\s+)?not\\s+(?:allowed|permitted)\\b");
    private static final List<FailureIntent> PRECEDENCE = List.of(UNIQUE, REMOVE, REQUIRE);

    private final List<Pattern> phrases;

    FailureIntent(List<Pattern> phrases) {
        this.phrases = phrases;
    }

    static Optional<FailureIntent> of(String lowerCaseMessage) {
        if (NULL_NOT_ALLOWED.matcher(lowerCaseMessage).find()) {
            return Optional.of(REQUIRE);
        }
        return PRECEDENCE.stream()
                .filter(intent -> intent.matches(lowerCaseMessage))
                .findFirst();
    }

    private boolean matches(String lowerCaseMessage) {
        return phrases.stream().anyMatch(phrase -> phrase.matcher(lowerCaseMessage).find());
    }
}
