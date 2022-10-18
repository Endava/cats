package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.api.Parser;
import com.endava.cats.json.JsonUtils;

/**
 * Parser used to evaluate expressions from a given request JSON payload.
 * The syntax for these variables is {@code $request.variable}.
 */
public class RequestVariableParser implements Parser {
    @Override
    public String parse(String expression, Object context) {
        return String.valueOf(JsonUtils.getVariableFromJson(String.valueOf(context), expression.replace("request", "").substring(2)));
    }
}
