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

    int STATUS_CREATED = 201;
    int STATUS_NO_CONTENT = 204;
    int STATUS_RESET_CONTENT = 205;
    int STATUS_NOT_MODIFIED = 304;
    int STATUS_BAD_REQUEST = 400;
    int STATUS_UNAUTHORIZED = 401;
    int STATUS_NOT_FOUND = 404;
    int STATUS_METHOD_NOT_ALLOWED = 405;
    int STATUS_NOT_ACCEPTABLE = 406;
    int STATUS_GONE = 410;
    int STATUS_PAYLOAD_TOO_LARGE = 413;
    int STATUS_UNSUPPORTED_MEDIA_TYPE = 415;
    int STATUS_UNPROCESSABLE_ENTITY = 422;
    int STATUS_UPGRADE_REQUIRED = 426;
    int STATUS_TOO_MANY_HEADERS = 431;
    int STATUS_INTERNAL_SERVER_ERROR = 500;
    int STATUS_NOT_IMPLEMENTED = 501;
    int MIN_VALID_HTTP_CODE = 100;
    int MAX_VALID_HTTP_CODE = 599;

    /**
     * Checks if the provided integer code is within the standard HTTP status code range ({@code 100..599}).
     *
     * @param code The integer response code to check.
     * @return {@code true} if the code is between 100 and 599 inclusive.
     */
    static boolean isValidHttpCode(int code) {
        return code >= MIN_VALID_HTTP_CODE && code <= MAX_VALID_HTTP_CODE;
    }

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
        return code == STATUS_TOO_MANY_HEADERS;
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
     * Checks if the provided integer code corresponds to a 5xx HTTP response status code.
     *
     * @param code The integer response code to check.
     * @return True if the code corresponds to a 5xx status code, false otherwise.
     */
    static boolean is5xxCode(int code) {
        return String.valueOf(code).startsWith("5");
    }

    /**
     * Checks if the provided integer code corresponds to an HTTP response status code indicating that the operation is unimplemented (501).
     *
     * @param code The integer response code to check.
     * @return True if the code corresponds to an unimplemented status code (501), false otherwise.
     */
    static boolean isUnimplemented(int code) {
        return code == STATUS_NOT_IMPLEMENTED;
    }

    static boolean isInternalServerError(int code) {
        return code == STATUS_INTERNAL_SERVER_ERROR;
    }

    static boolean isServerErrorOtherThan500And501(int code) {
        return is5xxCode(code) && !isInternalServerError(code) && !isUnimplemented(code);
    }

    static boolean isNotFoundOrGone(int code) {
        return code == STATUS_NOT_FOUND || code == STATUS_GONE;
    }

    static boolean isClientValidationError(int code) {
        return code == STATUS_BAD_REQUEST || code == STATUS_UNPROCESSABLE_ENTITY;
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
