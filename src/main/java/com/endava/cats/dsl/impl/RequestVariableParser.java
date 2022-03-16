package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.Parser;
import com.endava.cats.model.util.JsonUtils;

/**
 * Parser used to evaluate expressions from a given request JSON payload.
 * The syntax for these variables is {@code $request.variable}.
 */
public class RequestVariableParser implements Parser {
    @Override
    public String parse(String expression, String payload) {
        return String.valueOf(JsonUtils.getVariableFromJson(payload, expression.replace("request", "").substring(2)));
    }
}
