package com.endava.cats.report.problem;

import com.endava.cats.model.CatsResultFactory.Reason;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Reads result reasons as written in reports, where some carry a status suffix ({@code Unexpected response code: 422},
 * {@code Unexpected behaviour 400}) and some use the description of their {@link Reason}.
 */
final class ResultReasons {
    /**
     * The family of the generic status-code verdicts, which differ only in whether the contract documents the status.
     */
    static final String UNEXPECTED_STATUS = "Unexpected status";
    private static final Pattern STATUS_SUFFIX = Pattern.compile("[: ]+\\d{3}$");

    private ResultReasons() {
    }

    /**
     * Returns the result reason without its status suffix.
     *
     * @param resultReason the result reason of a test
     * @return the reason family, e.g. {@code Undocumented response code}
     */
    static String family(String resultReason) {
        return STATUS_SUFFIX.matcher(StringUtils.defaultString(resultReason).trim()).replaceFirst("");
    }

    /**
     * Returns the verdict family: {@link #UNEXPECTED_STATUS} for the generic status-code verdicts, the
     * {@link #family(String) family} otherwise.
     *
     * @param resultReason the result reason of a test
     * @return the verdict family
     */
    static String verdictFamily(String resultReason) {
        return isGenericStatusVerdict(resultReason) ? UNEXPECTED_STATUS : family(resultReason);
    }

    /**
     * Whether the result reason is a generic status-code verdict that says nothing about what was accepted.
     *
     * @param resultReason the result reason of a test
     * @return {@code true} for {@code Unexpected response code} and {@code Unexpected behaviour}
     */
    static boolean isGenericStatusVerdict(String resultReason) {
        return Reason.fromResultReason(resultReason)
                .filter(reason -> reason == Reason.UNEXPECTED_RESPONSE_CODE || reason == Reason.UNEXPECTED_BEHAVIOUR)
                .isPresent();
    }

    /**
     * Whether the result reason is the given reason.
     *
     * @param resultReason the result reason of a test
     * @param reason       the reason
     * @return {@code true} when the result reason resolves to {@code reason}
     */
    static boolean is(String resultReason, Reason reason) {
        return Reason.fromResultReason(resultReason).filter(reason::equals).isPresent();
    }
}
