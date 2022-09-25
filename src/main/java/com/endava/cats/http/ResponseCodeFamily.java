package com.endava.cats.http;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum ResponseCodeFamily {
    TWOXX {
        @Override
        public String getStartingDigit() {
            return "2";
        }

        @Override
        public String asString() {
            return "2XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("200", "201", "202", "204");
        }
    }, FOURXX {
        @Override
        public String getStartingDigit() {
            return "4";
        }

        @Override
        public String asString() {
            return "4XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("400", "413", "414", "422");
        }
    }, FOURXX_AA {
        @Override
        public String getStartingDigit() {
            return "4";
        }

        @Override
        public String asString() {
            return "401, 403";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("401", "403");
        }
    }, FOURXX_MT {
        @Override
        public String getStartingDigit() {
            return "4";
        }

        @Override
        public String asString() {
            return "4XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("406", "415");
        }
    }, FOURXX_NF {
        @Override
        public String getStartingDigit() {
            return "4";
        }

        @Override
        public String asString() {
            return "4XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Collections.singletonList("404");
        }
    }, FIVEXX {
        @Override
        public String getStartingDigit() {
            return "5";
        }

        @Override
        public String asString() {
            return "5XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("500", "501");
        }
    }, ONEXX {
        @Override
        public String getStartingDigit() {
            return "1";
        }

        @Override
        public String asString() {
            return "1XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("100", "101");
        }
    }, THREEXX {
        @Override
        public String getStartingDigit() {
            return "3";
        }

        @Override
        public String asString() {
            return "3XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Arrays.asList("300", "301", "302");
        }
    }, ZEROXX {
        @Override
        public String getStartingDigit() {
            return "0";
        }

        @Override
        public String asString() {
            return "0XX";
        }

        @Override
        public List<String> allowedResponseCodes() {
            return Collections.singletonList("000");
        }
    };

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
                || (codeOne.substring(1, 3).equalsIgnoreCase("xx") && codeOne.substring(0, 1).equalsIgnoreCase(codeTwo.substring(0, 1)))
                || (codeTwo.substring(1, 3).equalsIgnoreCase("xx") && codeTwo.substring(0, 1).equalsIgnoreCase(codeOne.substring(0, 1)));
    }

    public abstract String getStartingDigit();

    public abstract String asString();

    public abstract List<String> allowedResponseCodes();
}
