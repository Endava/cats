package com.endava.cats.dsl;

import com.endava.cats.dsl.impl.EnvVariableParser;
import com.endava.cats.dsl.impl.ShorthandFunctionParser;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves dynamic values supplied through CATS configuration files.
 *
 * <p>Unlike {@link CatsDSLParser}, this resolver also expands placeholders embedded
 * in larger values, such as {@code Bearer ${token}} or {@code Bearer $$API_TOKEN}, before evaluating a CATS DSL
 * expression.</p>
 */
public final class DynamicValueResolver {
    private static final ShorthandFunctionParser SHORTHAND_PARSER = new ShorthandFunctionParser();
    private static final EnvVariableParser ENV_PARSER = new EnvVariableParser();
    private static final Pattern EMBEDDED_ENV_PLACEHOLDER = Pattern.compile("\\$\\$[A-Za-z_]\\w*");

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
        if (result.contains("$$") && !result.trim().startsWith("$$")) {
            result = expandEmbeddedEnvVariables(result, context);
        }
        if (result.contains("#(") && !result.trim().startsWith("#(")) {
            result = SHORTHAND_PARSER.parse(result, context);
        }
        return CatsDSLParser.parseAndGetResult(result, context);
    }

    /**
     * Resolves a dynamic value and preserves scalar boolean/numeric types when the original expression
     * is a standalone {@code #(...)} or {@code T(...)} call.
     *
     * @param rawExpr the raw expression string
     * @param context variables available to the resolver
     * @return a Boolean, Long, Double, or String result
     */
    public static Object resolveAsTypedValue(String rawExpr, Map<String, String> context) {
        String resolved = resolve(rawExpr, context);
        return coerceScalarIfOriginalWasTyped(rawExpr, resolved);
    }

    public static Object coerceScalarIfOriginalWasTyped(String rawExpr, String resolved) {
        if (rawExpr == null || resolved == null) {
            return resolved;
        }
        String trimmed = rawExpr.trim();
        if (!trimmed.startsWith("#(") && !trimmed.startsWith("T(")) {
            return resolved;
        }
        if ("true".equalsIgnoreCase(resolved) || "false".equalsIgnoreCase(resolved)) {
            return Boolean.valueOf(resolved);
        }
        if (resolved.matches("-?\\d+")) {
            try {
                return Long.parseLong(resolved);
            } catch (NumberFormatException _) {
                return resolved;
            }
        }
        if (resolved.matches("-?\\d+\\.\\d+")) {
            try {
                return Double.parseDouble(resolved);
            } catch (NumberFormatException _) {
                return resolved;
            }
        }
        return resolved;
    }

    private static String expandEmbeddedEnvVariables(String input, Map<String, String> context) {
        Matcher matcher = EMBEDDED_ENV_PLACEHOLDER.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String resolved = ENV_PARSER.parse(matcher.group(), context);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
