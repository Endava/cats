package com.endava.cats.http;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public enum ResponseCodeFamily {
    TWOXX {
        @Override
        public String asString() {
            return "2XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("200", "201", "202", "204");
        }
    }, TWOXX_GENERIC {
        @Override
        public String asString() {
            return "2XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return List.of("2XX");
        }
    },
    FOURXX {
        @Override
        public String asString() {
            return "4XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("400", "413", "414", "422");
        }
    },
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

    public static final String XX = "XX";

    public static boolean isValidCode(String code) {
        return code != null && code.length() == 3 && Character.isDigit(code.charAt(0));
    }

    public static ResponseCodeFamily from(String responseCode) {
        for (ResponseCodeFamily value : values()) {
            if (value.allowedResponseCodes().contains(responseCode)) {
                return value;
            }
        }

        return ZEROXX;
    }

    public static boolean is2xxCode(int code) {
        return String.valueOf(code).startsWith("2");
    }

    public static boolean is4xxCode(int code) {
        return String.valueOf(code).startsWith("4");
    }

    public static boolean isUnimplemented(int code) {
        return code == 501;
    }

    public static Object[] getExpectedWordingBasedOnRequiredFields(boolean anyMandatory) {
        return new Object[]{
                getResultCodeBasedOnRequiredFieldsRemoved(anyMandatory).asString(),
                anyMandatory ? "were" : "were not"
        };
    }

    public static ResponseCodeFamily getResultCodeBasedOnRequiredFieldsRemoved(boolean required) {
        return required ? ResponseCodeFamily.FOURXX : ResponseCodeFamily.TWOXX;
    }

    public static boolean matchAsCodeOrRange(String codeOne, String codeTwo) {
        return codeOne.equalsIgnoreCase(codeTwo)
                || (codeOne.substring(1, 3).equalsIgnoreCase(XX) && codeOne.substring(0, 1).equalsIgnoreCase(codeTwo.substring(0, 1)))
                || (codeTwo.substring(1, 3).equalsIgnoreCase(XX) && codeTwo.substring(0, 1).equalsIgnoreCase(codeOne.substring(0, 1)));
    }

    public boolean matchesAllowedResponseCodes(String code) {
        boolean isAllowedResponseCode = this.allowedResponseCodes().contains(code);
        boolean isAllowedGenericResponseCode = this.allowedResponseCodes().stream()
                .filter(allowedCode -> allowedCode.endsWith(XX))
                .findFirst()
                .orElse("X")
                .charAt(0) == code.toUpperCase(Locale.ROOT).charAt(0);

        return isAllowedGenericResponseCode || isAllowedResponseCode;
    }

    public String getStartingDigit() {
        return String.valueOf(asString().charAt(0));
    }

    public abstract String asString();

    public abstract List<String> allowedResponseCodes();
}
