package com.endava.cats.http;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An enumeration representing different response code families along with their allowed response codes.
 */
public enum ResponseCodeFamilyPredefined implements ResponseCodeFamily {
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
    FOURXX_NF_AND_VALIDATION {
        @Override
        public String asString() {
            return "4XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return List.of("404", "400", "422");
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
    },
    FOURXX_TWOXX {
        @Override
        public String asString() {
            return "4XX|2XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return List.of("2XX", "4XX");
        }
    };

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
}
