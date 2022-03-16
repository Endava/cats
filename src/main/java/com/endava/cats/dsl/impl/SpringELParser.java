package com.endava.cats.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.json.JsonPropertyAccessor;

import java.util.Collections;
import java.util.Optional;

/**
 * Parser used to evaluate expression using Spring EL.
 * The format of these expressions usually start with {@code T{....}}.
 * Expression can also have access to the JSON elements supplied in the payload.
 */
public class SpringELParser implements Parser {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final SpelExpressionParser spelExpressionParser;
    private final ObjectMapper mapper;

    public SpringELParser() {
        spelExpressionParser = new SpelExpressionParser();
        mapper = new ObjectMapper();
    }

    @Override
    public String parse(String expression, String payload) {
        log.info("Parsing {}", expression);
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
