package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.model.CatsResponse;

import java.util.regex.Pattern;

/**
 * Arithmetic canary payloads and detectors for OS command injection.
 * <p>
 * Payloads embed multiplication of two 5-digit primes whose 10-digit product fits within signed 32-bit integers
 * (for POSIX {@code expr}, shell {@code $((...))}, and Windows {@code set /a}) and never appears in the payload text.
 * Replay confirmation substitutes a distinct second prime pair so stored echoes cannot masquerade as execution.
 * </p>
 */
public final class CommandCanary {
    public static final int PRIMARY_LEFT = 37199;
    public static final int PRIMARY_RIGHT = 43963;
    public static final String PRIMARY_PRODUCT = Long.toString((long) PRIMARY_LEFT * PRIMARY_RIGHT);

    public static final int CONFIRM_LEFT = 39119;
    public static final int CONFIRM_RIGHT = 46337;
    public static final String CONFIRM_PRODUCT = Long.toString((long) CONFIRM_LEFT * CONFIRM_RIGHT);

    private static final Pattern PRIMARY_TOKEN = standaloneToken(PRIMARY_PRODUCT);
    private static final Pattern CONFIRM_TOKEN = standaloneToken(CONFIRM_PRODUCT);
    private static final String PRIMARY_LEFT_STR = Integer.toString(PRIMARY_LEFT);
    private static final String PRIMARY_RIGHT_STR = Integer.toString(PRIMARY_RIGHT);

    private CommandCanary() {
    }

    public static String primaryShellArithmetic() {
        return "$((%d*%d))".formatted(PRIMARY_LEFT, PRIMARY_RIGHT);
    }

    public static String primaryExprSemicolon() {
        return "; expr %d \\* %d".formatted(PRIMARY_LEFT, PRIMARY_RIGHT);
    }

    public static String primaryExprPipe() {
        return "| expr %d \\* %d".formatted(PRIMARY_LEFT, PRIMARY_RIGHT);
    }

    public static String primaryExprBackticks() {
        return "`expr %d \\* %d`".formatted(PRIMARY_LEFT, PRIMARY_RIGHT);
    }

    public static String primarySingleQuotedEcho() {
        return "'; echo $((%d*%d)); '".formatted(PRIMARY_LEFT, PRIMARY_RIGHT);
    }

    public static String primaryWindowsSetArithmetic() {
        return "& set /a %d*%d".formatted(PRIMARY_LEFT, PRIMARY_RIGHT);
    }

    /**
     * Checks whether the payload is a primary command-injection arithmetic canary.
     *
     * @param payload the injected payload
     * @return true when the payload carries the primary prime operands
     */
    public static boolean isPrimaryCanaryPayload(String payload) {
        return payload != null && payload.contains(PRIMARY_LEFT_STR) && payload.contains(PRIMARY_RIGHT_STR);
    }

    /**
     * Rewrites a primary canary payload to use the independent confirmation prime pair.
     *
     * @param primaryPayload the primary canary payload
     * @return the confirmation payload using the secondary prime pair
     */
    public static String toConfirmationPayload(String primaryPayload) {
        return primaryPayload
                .replace(PRIMARY_LEFT_STR, Integer.toString(CONFIRM_LEFT))
                .replace(PRIMARY_RIGHT_STR, Integer.toString(CONFIRM_RIGHT));
    }

    /**
     * Checks whether the stripped response contains the computed primary product as a standalone number.
     *
     * @param response the reflection-stripped response
     * @return true if the primary product is present
     */
    public static boolean containsPrimaryProduct(CatsResponse response) {
        return containsToken(response, PRIMARY_TOKEN);
    }

    /**
     * Checks whether the confirmation response (after stripping the confirmation payload) contains the secondary product.
     *
     * @param response            the raw replay response
     * @param confirmationPayload the confirmation payload sent on replay
     * @return true if the confirmation product is present
     */
    public static boolean matchesConfirmation(CatsResponse response, String confirmationPayload) {
        CatsResponse stripped = PayloadReflections.strip(response, confirmationPayload);
        return containsToken(stripped, CONFIRM_TOKEN);
    }

    private static boolean containsToken(CatsResponse response, Pattern tokenPattern) {
        if (response == null || response.getBody() == null || response.getBody().isEmpty()) {
            return false;
        }
        return tokenPattern.matcher(response.getBody()).find();
    }

    private static Pattern standaloneToken(String number) {
        return Pattern.compile("(?<![0-9])" + Pattern.quote(number) + "(?![0-9])");
    }
}
