package com.endava.cats.dsl.api;

/**
 * A {@code Parser} is used to interpret different types of expressions found in CATS configuration files. These can range from
 * environment variables to dynamic expression (like Spring EL).
 */
public interface Parser {

    /**
     * Parses the given expression within the given context and returns the result.
     *
     * @param expression the expression that needs to be extracted from the context
     * @param context    a given context; can be a JSON payload, a Map of data, etc
     * @return the result of the parsing within the given context
     */
    String parse(String expression, Object context);
}
