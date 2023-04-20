package com.endava.cats.dsl;

import com.endava.cats.dsl.api.Parser;
import com.endava.cats.dsl.impl.AuthScriptProviderParser;
import com.endava.cats.dsl.impl.EnvVariableParser;
import com.endava.cats.dsl.impl.SpringELParser;

import java.util.Map;

public class CatsDSLParser {
    private static final Map<String, Parser> PARSERS = Map.of(
            "$$", new EnvVariableParser(),
            "auth_script", new AuthScriptProviderParser());

    private static final Parser DEFAULT_PARSER = new SpringELParser();

    /**
     * Gets the appropriate parser based on the {@code valueFromFile} and runs it against the given payload.
     * If no Parser is found, it will default to {@link SpringELParser}.
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
     * Transforms various ways of describing the expressions like: ${request.value} which is equivalent to request.value
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
