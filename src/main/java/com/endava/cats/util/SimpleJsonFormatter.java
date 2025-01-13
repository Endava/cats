package com.endava.cats.util;

/**
 * Simple utility class to format JSON strings.
 * It provides methods to check if a string looks like JSON and to format it with indentation.
 */
public abstract class SimpleJsonFormatter {

    private SimpleJsonFormatter() {
        // Utility class, no instantiation allowed
    }

    private static boolean looksLikeJson(String str) {
        String trimmed = str.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static String formatJsonString(String jsonStr) {
        StringBuilder formatted = new StringBuilder();
        int indentLevel = 0;
        boolean inString = false;
        boolean escapeNext = false;
        String indent = "  ";

        for (int i = 0; i < jsonStr.length(); i++) {
            char currentChar = jsonStr.charAt(i);
            char prevChar = i > 0 ? jsonStr.charAt(i - 1) : '\0';
            char nextChar = i < jsonStr.length() - 1 ? jsonStr.charAt(i + 1) : '\0';

            if (escapeNext) {
                formatted.append(currentChar);
                escapeNext = false;
                continue;
            }

            if (currentChar == '\\' && inString) {
                formatted.append(currentChar);
                escapeNext = true;
                continue;
            }

            if (currentChar == '"' && prevChar != '\\') {
                inString = !inString;
            }

            if (!inString) {
                switch (currentChar) {
                    case '{', '[' -> {
                        formatted.append(currentChar);
                        indentLevel++;
                        if (nextChar != '}' && nextChar != ']') {
                            formatted.append('\n').append(indent.repeat(Math.max(0, indentLevel)));
                        }
                    }
                    case '}', ']' -> {
                        if (prevChar != '{' && prevChar != '[' && prevChar != '\n') {
                            formatted.append('\n').append(indent.repeat(Math.max(0, indentLevel - 1)));
                        }
                        indentLevel--;
                        formatted.append(currentChar);
                    }
                    case ',' -> {
                        formatted.append(currentChar);
                        if (nextChar != '\n') {
                            formatted.append('\n').append(indent.repeat(Math.max(0, indentLevel)));
                        }
                    }
                    case ':' -> formatted.append(currentChar).append(' ');
                    case ' ', '\t', '\n', '\r' -> {
                        // Skip whitespace outside strings
                    }
                    default -> formatted.append(currentChar);
                }
            } else {
                formatted.append(currentChar);
            }
        }

        return formatted.toString();
    }


    /**
     * Formats a JSON string if it looks like JSON.
     *
     * @param input the input string to format
     * @return the formatted JSON string or the original string if it does not look like JSON
     */
    public static String formatJson(String input) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }

        if (looksLikeJson(input)) {
            return formatJsonString(input);
        } else {
            return input;
        }
    }
}
