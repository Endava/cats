package com.endava.cats.command.triage;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Sanitizes report-derived text (server error messages, paths) before embedding it in generated YAML or shell
 * comments, so a crafted response body can never inject new lines into {@code reproducers.sh} or YAML artifacts.
 */
final class ReportText {
    private static final int MAX_COMMENT_LENGTH = 160;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]+");

    private ReportText() {
    }

    static String singleLine(String text) {
        String flattened = CONTROL_CHARACTERS.matcher(StringUtils.defaultString(text)).replaceAll(" ").trim();
        return StringUtils.abbreviate(flattened, "...", MAX_COMMENT_LENGTH);
    }
}
