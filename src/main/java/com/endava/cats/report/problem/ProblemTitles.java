package com.endava.cats.report.problem;

import com.endava.cats.model.CatsResultFactory.Reason;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Kind-specific problem titles. Only the message kinds show the server message; the other kinds never use the body.
 */
final class ProblemTitles {
    private static final Pattern WORD_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    private ProblemTitles() {
    }

    static String of(ProblemKey key, int status, String serverMessage) {
        String first = key.parts().getFirst();
        return switch (key.kind()) {
            case SERVER_ERROR -> withMessage("Server error (HTTP %d)".formatted(status), serverMessage);
            case ERROR_MESSAGE -> withMessage("%s (HTTP %d)".formatted(rejectionLabel(first), status), serverMessage);
            case RESPONSE_VIOLATION -> violation(first, key.parts().getLast());
            case STATUS_CODE -> "%s: %d".formatted(first, status);
            case ACCEPTED_REQUEST -> accepted(key);
            case NO_RESPONSE -> first;
        };
    }

    /**
     * Turns a fuzzer name into words, e.g. {@code NumericValuesInStringFields} into {@code Numeric values in string fields}.
     *
     * @param fuzzer the fuzzer name
     * @return the fuzzer name as a sentence fragment
     */
    static String humanize(String fuzzer) {
        String words = WORD_BOUNDARY.matcher(StringUtils.defaultString(fuzzer)).replaceAll(" ").toLowerCase(Locale.ROOT);
        return StringUtils.capitalize(words);
    }

    private static String withMessage(String label, String serverMessage) {
        return StringUtils.isBlank(serverMessage) ? label : "%s: %s".formatted(label, serverMessage);
    }

    private static String rejectionLabel(String verdictFamily) {
        return ResultReasons.UNEXPECTED_STATUS.equals(verdictFamily) ? "Request rejected" : verdictFamily;
    }

    private static String violation(String reason, String violation) {
        return ResultReasons.is(reason, Reason.NOT_MATCHING_RESPONSE_SCHEMA)
                ? "Response does not match the schema " + violation
                : "%s: %s".formatted(reason, violation);
    }

    private static String accepted(ProblemKey key) {
        String family = key.parts().get(1);
        return ResultReasons.UNEXPECTED_STATUS.equals(key.parts().getFirst())
                ? "%s accepted (HTTP %s)".formatted(humanize(key.parts().getLast()), family)
                : "%s (HTTP %s)".formatted(key.parts().getFirst(), family);
    }
}
