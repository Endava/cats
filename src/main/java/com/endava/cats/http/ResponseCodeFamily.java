package com.endava.cats.http;

import java.util.List;
import java.util.Locale;

/**
 * Defines response code sets that can be used for matching.
 */
public interface ResponseCodeFamily {
    /**
     * Gets the starting digit of the response code family as a string.
     *
     * @return The starting digit of the response code family.
     */
    default String getStartingDigit() {
        return String.valueOf(asString().charAt(0));
    }

    /**
     * Abstract method to get the string representation of the response code family.
     *
     * @return The string representation of the response code family.
     */
    String asString();

    /**
     * Abstract method to get the list of allowed response codes for the family.
     *
     * @return The list of allowed response codes for the family.
     */
    List<String> allowedResponseCodes();

    /**
     * A constant representing the string "XX".
     */
    String XX = "XX";

    /**
     * Checks if the provided code is a valid HTTP status code.
     *
     * @param code The response code to check.
     * @return True if the code is a valid HTTP status code, false otherwise.
     */
    static boolean isValidCode(String code) {
        if (code == null || code.length() != 3) {
            return false;
        }
        char firstDigit = code.charAt(0);
        return Character.isDigit(firstDigit) && Character.digit(firstDigit, 10) >= 1 && Character.digit(firstDigit, 10) <= 5;
    }


    static boolean isTooManyHeaders(int code) {
        return code == 431;
    }

    /**
     * Checks if the provided integer code corresponds to a 2xx HTTP response status code.
     *
     * @param code The integer response code to check.
     * @return True if the code corresponds to a 2xx status code, false otherwise.
     */
    static boolean is2xxCode(int code) {
        return String.valueOf(code).startsWith("2");
    }

    /**
     * Checks if the provided integer code corresponds to a 4xx HTTP response status code.
     *
     * @param code The integer response code to check.
     * @return True if the code corresponds to a 4xx status code, false otherwise.
     */
    static boolean is4xxCode(int code) {
        return String.valueOf(code).startsWith("4");
    }

    /**
     * Checks if the provided integer code corresponds to an HTTP response status code indicating that the operation is unimplemented (501).
     *
     * @param code The integer response code to check.
     * @return True if the code corresponds to an unimplemented status code (501), false otherwise.
     */
    static boolean isUnimplemented(int code) {
        return code == 501;
    }


    /**
     * Checks if two response codes match as an exact match, a generic range match, or a reversed generic range match.
     *
     * @param codeOne The first response code.
     * @param codeTwo The second response code.
     * @return {@code true} if the codes match as an exact match, a generic range match, or a reversed generic range match,
     * {@code false} otherwise.
     */
    static boolean matchAsCodeOrRange(String codeOne, String codeTwo) {
        return codeOne.equalsIgnoreCase(codeTwo)
                || (codeOne.substring(1, 3).equalsIgnoreCase(XX) && codeOne.substring(0, 1).equalsIgnoreCase(codeTwo.substring(0, 1)))
                || (codeTwo.substring(1, 3).equalsIgnoreCase(XX) && codeTwo.substring(0, 1).equalsIgnoreCase(codeOne.substring(0, 1)));
    }

    /**
     * Checks if the provided response code matches any allowed response codes or their generic forms.
     *
     * @param code The response code to check.
     * @return {@code true} if the code matches any allowed response codes or their generic forms, {@code false} otherwise.
     */
    default boolean matchesAllowedResponseCodes(String code) {
        boolean isAllowedResponseCode = this.allowedResponseCodes().contains(code);
        boolean isAllowedGenericResponseCode = this.allowedResponseCodes().stream()
                .filter(allowedCode -> allowedCode.endsWith(XX))
                .anyMatch(allowedCode -> allowedCode.charAt(0) == code.toUpperCase(Locale.ROOT).charAt(0));

        return isAllowedGenericResponseCode || isAllowedResponseCode;
    }

}
