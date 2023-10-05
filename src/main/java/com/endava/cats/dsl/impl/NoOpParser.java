package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.api.Parser;

import java.util.Map;

/**
 * No operation parser. Returns the same input expression.
 */
public class NoOpParser implements Parser {
    @Override
    public String parse(String expression, Map<String, String> context) {
        return expression;
    }
}
