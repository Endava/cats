package com.endava.cats.dsl;

import com.endava.cats.util.JsonUtils;

public class RequestVariableParser implements Parser {
    @Override
    public String parse(String expression, String payload) {
        return String.valueOf(JsonUtils.getVariableFromJson(payload, expression.replace("request", "").substring(2)));
    }
}
