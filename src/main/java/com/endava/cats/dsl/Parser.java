package com.endava.cats.dsl;

/**
 * A {@code Parser} is used to interpret different types of expressions found in CATS configuration files. These can range from
 * environment variables to dynamic expression (like Spring EL).
 */
public interface Parser {

    String parse(String expression, String payload);
}
