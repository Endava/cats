package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.api.Parser;
import com.endava.cats.json.JsonUtils;

import java.util.Map;

/**
 * Parser used to evaluate expressions from a given request JSON payload.
 * The syntax for these variables is {@code $request.variable}.
 */
public class RequestVariableParser implements Parser {
    @Override
    public String parse(String expression, Map<String, String> context) {
        return String.valueOf(JsonUtils.getVariableFromJson(context.get(Parser.REQUEST),
                expression.replace("request", "").substring(2)));
    }
}
