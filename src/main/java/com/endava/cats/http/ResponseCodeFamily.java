package com.endava.cats.http;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * An enumeration representing different response code families along with their allowed response codes.
 */
public enum ResponseCodeFamily {
    /**
     * Represents the 2XX response code family with 200,201,202,204 as allowed codes.
     */
    TWOXX {
        @Override
        public String asString() {
            return "2XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("200", "201", "202", "204");
        }
    },
    /**
     * Represents the 2XX response code family generically.
     */
    TWOXX_GENERIC {
        @Override
        public String asString() {
            return "2XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return List.of("2XX");
        }
    },
    /**
     * Represents the 4XX response code family with 400, 413, 414, 422, 431 as allowed codes.
     */
    FOURXX {
        @Override
        public String asString() {
            return "4XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("400", "413", "414", "422", "431");
        }
    },
    /**
     * Represents the 4XX response code family generically.
     */
    FOURXX_GENERIC {
        @Override
        public String asString() {
            return "4XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return List.of("4XX");
        }
    },
    /**
     * Represents the 4XX response code family with authentication and authorization errors.
     */
    FOURXX_AA {
        @Override
        public String asString() {
            return "401, 403";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("401", "403");
        }
    },
    /**
     * Represents the 4XX response code family with media type errors.
     */
    FOURXX_MT {
        @Override
        public String asString() {
            return "4XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("406", "415");
        }
    },
    /**
     * Represents the 404 response code.
     */
    FOURXX_NF {
        @Override
        public String asString() {
            return "4XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Collections.singletonList("404");
        }
    },
    /**
     * Represents the 500 and 501 response codes.
     */
    FIVEXX {
        @Override
        public String asString() {
            return "5XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("500", "501");
        }
    },
    /**
     * Represents the generic 5XX response code family.
     */
    FIVEXX_GENERIC {
        @Override
        public String asString() {
            return "5XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return List.of("5XX");
        }
    },
    /**
     * Represents the 1XX response code family generically.
     */
    ONEXX {
        @Override
        public String asString() {
            return "1XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("100", "101", "1XX");
        }
    },
    /**
     * Represents the 3XX response code family generically.
     */
    THREEXX {
        @Override
        public String asString() {
            return "3XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("300", "301", "302", "3XX");
        }
    },

    /**
     * Represents the 0XX response code family.
     */
    ZEROXX {
        @Override
        public String asString() {
            return "0XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Collections.singletonList("000");
        }
    },
    /**
     * Represents the 400|501 response code family.
     */
    FOUR00_FIVE01 {
        @Override
        public String asString() {
            return "400|501";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return List.of("400", "501");
        }
    };

    /**
     * A constant representing the string "XX".
     */
    public static final String XX = "XX";

    /**
     * Checks if the provided code is a valid HTTP status code.
     *
     * @param code The response code to check.
     * @return True if the code is a valid HTTP status code, false otherwise.
     */
    public static boolean isValidCode(String code) {
        return code != null && code.length() == 3 && Character.isDigit(code.charAt(0));
    }

    /**
     * Retrieves the ResponseCodeFamily corresponding to the given response code.
     *
     * @param responseCode The HTTP response code.
     * @return The ResponseCodeFamily corresponding to the response code.
     */
    public static ResponseCodeFamily from(String responseCode) {
        for (ResponseCodeFamily value : values()) {
            if (value.allowedResponseCodes().contains(responseCode)) {
                return value;
            }
        }

        return ZEROXX;
    }

    public static boolean isTooManyHeaders(int code) {
        return code == 431;
    }

    /**
     * Checks if the provided integer code corresponds to a 2xx HTTP response status code.
     *
     * @param code The integer response code to check.
     * @return True if the code corresponds to a 2xx status code, false otherwise.
     */
    public static boolean is2xxCode(int code) {
        return String.valueOf(code).startsWith("2");
    }

    /**
     * Checks if the provided integer code corresponds to a 4xx HTTP response status code.
     *
     * @param code The integer response code to check.
     * @return True if the code corresponds to a 4xx status code, false otherwise.
     */
    public static boolean is4xxCode(int code) {
        return String.valueOf(code).startsWith("4");
    }

    /**
     * Checks if the provided integer code corresponds to an HTTP response status code indicating that the operation is unimplemented (501).
     *
     * @param code The integer response code to check.
     * @return True if the code corresponds to an unimplemented status code (501), false otherwise.
     */
    public static boolean isUnimplemented(int code) {
        return code == 501;
    }

    /**
     * Generates an array of objects representing expected wording based on the presence of required fields.
     *
     * @param anyMandatory Flag indicating the presence of any mandatory fields.
     * @return An array of objects containing the expected wording based on the required fields.
     * - The first element is the result code based on whether required fields were removed.
     * - The second element is a string indicating whether required fields were present or not.
     */
    public static Object[] getExpectedWordingBasedOnRequiredFields(boolean anyMandatory) {
        return new Object[]{
                getResultCodeBasedOnRequiredFieldsRemoved(anyMandatory).asString(),
                anyMandatory ? "were" : "were not"
        };
    }

    /**
     * Determines the response code family based on the presence or absence of required fields.
     *
     * @param required Flag indicating the presence of required fields.
     * @return The response code family:
     * - If required is true, returns the 4XX family.
     * - If required is false, returns the 2XX family.
     */
    public static ResponseCodeFamily getResultCodeBasedOnRequiredFieldsRemoved(boolean required) {
        return required ? ResponseCodeFamily.FOURXX : ResponseCodeFamily.TWOXX;
    }

    /**
     * Checks if two response codes match as an exact match, a generic range match, or a reversed generic range match.
     *
     * @param codeOne The first response code.
     * @param codeTwo The second response code.
     * @return {@code true} if the codes match as an exact match, a generic range match, or a reversed generic range match,
     * {@code false} otherwise.
     */
    public static boolean matchAsCodeOrRange(String codeOne, String codeTwo) {
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
    public boolean matchesAllowedResponseCodes(String code) {
        boolean isAllowedResponseCode = this.allowedResponseCodes().contains(code);
        boolean isAllowedGenericResponseCode = this.allowedResponseCodes().stream()
                .filter(allowedCode -> allowedCode.endsWith(XX))
                .findFirst()
                .orElse("X")
                .charAt(0) == code.toUpperCase(Locale.ROOT).charAt(0);

        return isAllowedGenericResponseCode || isAllowedResponseCode;
    }

    /**
     * Gets the starting digit of the response code family as a string.
     *
     * @return The starting digit of the response code family.
     */
    public String getStartingDigit() {
        return String.valueOf(asString().charAt(0));
    }

    /**
     * Abstract method to get the string representation of the response code family.
     *
     * @return The string representation of the response code family.
     */
    public abstract String asString();

    /**
     * Abstract method to get the list of allowed response codes for the family.
     *
     * @return The list of allowed response codes for the family.
     */
    public abstract List<String> allowedResponseCodes();
}
