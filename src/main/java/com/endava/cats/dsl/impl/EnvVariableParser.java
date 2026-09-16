package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.api.Parser;

import java.util.Map;

/**
 * Parser used to retrieve environment variables. The variables are in the {@code $$variable} format.
 */
public class EnvVariableParser implements Parser {
    private static final String ENV_VARIABLE_NOT_FOUND = "not_found_";

    public static boolean isMissing(String value) {
        return value != null && value.startsWith(ENV_VARIABLE_NOT_FOUND + "$$");
    }

    public static String variableName(String expression) {
        return expression.replace("$", "");
    }

    @Override
    public String parse(String expression, Map<String, String> context) {
        String variableName = variableName(expression);
        String result = System.getenv(variableName);
        if (result == null && context != null) {
            result = context.get(variableName);
        }
        return result == null ? ENV_VARIABLE_NOT_FOUND + expression : result;
    }
}
