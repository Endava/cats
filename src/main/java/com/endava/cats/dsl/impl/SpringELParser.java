package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.api.Parser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.json.JsonPropertyAccessor;

import java.util.List;
import java.util.Map;

/**
 * Parser used to evaluate expression using Spring EL.
 * The format of these expressions usually start with {@code T{....}}.
 * Expression can also have access to the JSON elements supplied in the payload.
 */
public class SpringELParser implements Parser {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final SpelExpressionParser spelExpressionParser;

    public SpringELParser() {
        spelExpressionParser = new SpelExpressionParser();
    }

    @Override
    public String parse(String expression, Map<String, String> context) {
        log.debug("Parsing {}", expression);
        Object result = parseContext(expression, context);

        if (expression.equalsIgnoreCase(String.valueOf(result))) {
            result = parseContext(expression, context.getOrDefault(Parser.RESPONSE, null));
        }

        return result == null ? expression : String.valueOf(result);
    }

    @Nullable
    private Object parseContext(String expression, Object context) {
        try {
            StandardEvaluationContext evaluationContext = new StandardEvaluationContext(context);
            evaluationContext.setPropertyAccessors(List.of(new MapAccessor(), new JsonPropertyAccessor()));

            return spelExpressionParser.parseExpression(expression).getValue(evaluationContext);
        } catch (Exception e) {
            log.debug("Something went wrong while parsing!", e);
            return expression;
        }
    }
}
