package com.endava.cats.dsl.api;

import java.util.Map;

/**
 * A {@code Parser} is used to interpret different types of expressions found in CATS configuration files. These can range from
 * environment variables to dynamic expression (like Spring EL).
 */
public interface Parser {
    /**
     * Holds the name of the request object.
     */
    String REQUEST = "request";
    /**
     * Holds the name for the response object.
     */
    String RESPONSE = "response";

    /**
     * Holds the name for the path object.
     */
    String PATH = "path";
    /**
     * Holds the name for the auth script name variable.
     */
    String AUTH_SCRIPT = "auth_script";
    /**
     * Holds the name for the auth script refresh interval variable.
     */
    String AUTH_REFRESH = "auth_refresh";

    /**
     * Parses the given expression within the given context and returns the result.
     *
     * @param expression the expression that needs to be extracted from the context
     * @param context    a given context; can be a JSON payload, a Map of data, etc
     * @return the result of the parsing within the given context
     */
    String parse(String expression, Map<String, String> context);
}
