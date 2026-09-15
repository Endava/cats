package com.endava.cats.dsl;

import java.util.Map;

/**
 * Resolves dynamic values supplied through CATS configuration files.
 *
 * <p>Unlike {@link CatsDSLParser}, this resolver also expands placeholders embedded
 * in larger values, such as {@code Bearer ${token}}, before evaluating a CATS DSL
 * expression.</p>
 */
public final class DynamicValueResolver {

    private DynamicValueResolver() {
        // Utility class.
    }

    /**
     * Replaces known output variables and evaluates any remaining CATS DSL expression.
     *
     * @param value   value to resolve
     * @param context variables available to the resolver
     * @return the value returned by the CATS DSL parser
     */
    public static String resolve(String value, Map<String, String> context) {
        if (value == null) {
            return null;
        }

        String result = value;
        if (context != null) {
            for (Map.Entry<String, String> variable : context.entrySet()) {
                result = result.replace("${" + variable.getKey() + "}", variable.getValue());
            }
        }
        return CatsDSLParser.parseAndGetResult(result, context);
    }
}
