package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.api.Parser;

import java.util.Map;

/**
 * Parser used to retrieve environment variables. The variables are in the {@code $$variable} format.
 */
public class EnvVariableParser implements Parser {
    private static final String ENV_VARIABLE_NOT_FOUND = "not_found_";

    @Override
    public String parse(String expression, Map<String, String> context) {
        String result = System.getenv(expression.substring(2));
        return result == null ? ENV_VARIABLE_NOT_FOUND + expression : result;
    }
}
