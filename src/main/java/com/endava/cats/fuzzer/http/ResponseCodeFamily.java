package com.endava.cats.fuzzer.http;

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
    }, FOURXX {
        @Override
        public String getStartingDigit() {
            return "4";
        }

        @Override
        public String asString() {
            return "4XX";
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
    }, ONEXX {
        @Override
        public String getStartingDigit() {
            return "1";
        }

        @Override
        public String asString() {
            return "1XX";
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
    }, ZEROXX {
        @Override
        public String getStartingDigit() {
            return "0";
        }

        @Override
        public String asString() {
            return "0XX";
        }
    };

    public static boolean isValidCode(String code) {
        return code != null && code.length() == 3 && Character.isDigit(code.charAt(0));
    }

    public static ResponseCodeFamily from(String responseCode) {
        for (ResponseCodeFamily value : values()) {
            if (responseCode.charAt(0) == value.getStartingDigit().charAt(0)) {
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

    public abstract String getStartingDigit();

    public abstract String asString();
}
