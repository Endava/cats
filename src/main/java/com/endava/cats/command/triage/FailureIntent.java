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
    UNIQUE(Pattern.compile("\\balready\\s+(?:exists|in\\s+use|taken|registered)\\b|\\bduplicate\\b|\\bmust\\s+be\\s+unique\\b|\\bnot\\s+unique\\b")),
    REMOVE(Pattern.compile("\\bmust\\s+be\\s+(?:null|empty|absent|omitted)\\b"
            + "|\\b(?:must|should)\\s+not\\s+be\\s+(?:present|provided|set|specified|sent|supplied)\\b"
            + "|\\bcannot\\s+be\\s+(?:set|provided|specified|supplied)\\b"
            + "|\\bnot\\s+(?:allowed|permitted)\\b|\\bforbidden\\b"
            + "|\\b(?:unrecognized|unknown|unexpected)\\s+(?:field|property|attribute)\\b")),
    REQUIRE(Pattern.compile("\\b(?:is|are)\\s+(?:required|mandatory|missing)\\b"
            + "|\\b(?:must|should)\\s+not\\s+be\\s+(?:null|blank|empty)\\b|\\bcannot\\s+be\\s+(?:null|blank|empty)\\b"
            + "|\\bmust\\s+(?:be\\s+(?:provided|specified|present|set)|have|contain)\\b"
            + "|\\brequires?\\b|\\bmissing\\b"));

    private static final Pattern NULL_NOT_ALLOWED = Pattern.compile("\\b(?:null|empty|blank)\\s+(?:values?\\s+)?(?:is\\s+|are\\s+)?not\\s+(?:allowed|permitted)\\b");
    private static final List<FailureIntent> PRECEDENCE = List.of(UNIQUE, REMOVE, REQUIRE);

    private final Pattern phrase;

    FailureIntent(Pattern phrase) {
        this.phrase = phrase;
    }

    static Optional<FailureIntent> of(String lowerCaseMessage) {
        if (NULL_NOT_ALLOWED.matcher(lowerCaseMessage).find()) {
            return Optional.of(REQUIRE);
        }
        return PRECEDENCE.stream()
                .filter(intent -> intent.phrase.matcher(lowerCaseMessage).find())
                .findFirst();
    }
}
