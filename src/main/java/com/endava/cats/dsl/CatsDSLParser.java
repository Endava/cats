package com.endava.cats.dsl;

import com.endava.cats.dsl.api.Parser;
import com.endava.cats.dsl.impl.AuthScriptProviderParser;
import com.endava.cats.dsl.impl.EnvVariableParser;
import com.endava.cats.dsl.impl.NoOpParser;
import com.endava.cats.dsl.impl.SpringELParser;

import java.util.Map;

/**
 * Allows parsing of different type of dynamic values through different parsers.
 */
public class CatsDSLParser {
    private static final Parser DEFAULT_PARSER = new NoOpParser();
    private static final Parser SPRING_EL_PARSER = new SpringELParser();
    private static final Map<String, Parser> PARSERS = Map.of(
            "$$", new EnvVariableParser(),
            "$request", SPRING_EL_PARSER,
            "T(", SPRING_EL_PARSER,
            "${", SPRING_EL_PARSER,
            "auth_script", new AuthScriptProviderParser());

    private CatsDSLParser() {
        //ntd
    }

    /**
     * Gets the appropriate parser based on the {@code valueFromFile} and runs it against the given payload.
     * If no Parser is found, it will default to {@link NoOpParser}.
     *
     * @param valueFromFile the expression retrieved from the CATS files
     * @param context       a given context: JSON request or response, a Map of values
     * @return the result after the appropriate parser runs
     */
    public static String parseAndGetResult(String valueFromFile, Map<String, String> context) {
        return PARSERS.entrySet()
                .stream()
                .filter(entry -> valueFromFile.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(DEFAULT_PARSER)
                .parse(sanitize(valueFromFile), context);
    }

    /**
     * Transforms various ways of describing the expressions like: ${request.value} which is equivalent to 'request.value'
     * or request#value which is equivalent to request.value.
     *
     * @param expression the expression as supplied by the user
     * @return normalized form of the expression
     */
    private static String sanitize(String expression) {
        return expression.replaceAll("\\$\\{([^}]*)}", "$1")
                .replace("request#", "request.")
                .replace("$request", "request");
    }
}
