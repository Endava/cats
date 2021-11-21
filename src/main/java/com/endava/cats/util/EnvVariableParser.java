package com.endava.cats.util;

public class EnvVariableParser implements Parser {
    private static final String ENV_VARIABLE_NOT_FOUND = "not_found_";

    @Override
    public String parse(String expression, String payload) {
        String result = System.getenv(expression.substring(1));
        return result == null ? ENV_VARIABLE_NOT_FOUND + expression : result;
    }
}
