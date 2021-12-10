package com.endava.cats.dsl;

import com.endava.cats.util.Parser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.json.JsonPropertyAccessor;

import java.util.Collections;
import java.util.Optional;

public class SimpleParser implements Parser {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final SpelExpressionParser spelExpressionParser;
    private final ObjectMapper mapper;

    public SimpleParser() {
        spelExpressionParser = new SpelExpressionParser();
        mapper = new ObjectMapper();
    }

    @Override
    public String parse(String expression, String payload) {

        try {
            JsonNode jsonObject = mapper.readTree(Optional.ofNullable(payload).orElse(""));
            StandardEvaluationContext context = new StandardEvaluationContext(jsonObject);
            context.setPropertyAccessors(Collections.singletonList(new JsonPropertyAccessor()));

            return String.valueOf(spelExpressionParser.parseExpression(expression).getValue(context));
        } catch (Exception e) {
            log.error("Failed to parse {} as invalid syntax: {}", expression, e.getMessage());
            return expression;
        }
    }
}
