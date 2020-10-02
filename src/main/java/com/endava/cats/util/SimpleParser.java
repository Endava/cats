package com.endava.cats.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.spel.standard.SpelExpressionParser;

@Slf4j
public class SimpleParser implements Parser {
    private SpelExpressionParser spelExpressionParser;

    public SimpleParser() {
        spelExpressionParser = new SpelExpressionParser();
    }

    @Override
    public String parse(String input) {
        try {
            return String.valueOf(spelExpressionParser.parseExpression(input).getValue());
        } catch (Exception e) {
            log.error("Failed to parse {} as invalid syntax: {}", input, e.getMessage());
            return input;
        }
    }
}
