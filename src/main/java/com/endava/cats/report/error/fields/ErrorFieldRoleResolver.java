package com.endava.cats.report.error.fields;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Assigns {@link ErrorFieldRole}s by the last word of a field name, whatever its spelling: camelCase
 * ({@code errorMessage}), snake_case ({@code error_description}), kebab-case ({@code reason-code}) or upper case
 * ({@code ERROR_CODE}).
 * <p>
 * The rules form an ordered table; the first rule that matches the last word wins. Short words must match
 * completely, so {@code context} is not a text and {@code barcode} is not a code. Longer words also match as a suffix
 * of a compound written in lower case ({@code frontendfeilmelding}).
 */
public final class ErrorFieldRoleResolver implements ErrorFieldRoles {
    public static final ErrorFieldRoleResolver BY_NAME = new ErrorFieldRoleResolver();
    private static final Pattern WORD_BOUNDARY = Pattern.compile("[^A-Za-z0-9]+|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final Set<String> TRANSPORT_WORDS = Set.of("status", "http", "response");
    private static final List<RoleRule> RULES = List.of(
            new RoleRule(ErrorFieldRole.TYPE, Set.of("code", "codes", "type", "types", "kind", "kinds"), Set.of()),
            new RoleRule(ErrorFieldRole.MESSAGE, Set.of("msg", "msgs", "reason", "reasons", "text", "texts", "faultstring"),
                    Set.of("message", "messages", "description", "descriptions", "detail", "details", "melding", "meldinger",
                            "meldung", "mensaje", "mensagem", "nachricht", "beschreibung", "messaggio", "bericht", "meddelande")));

    private ErrorFieldRoleResolver() {
    }

    @Override
    public Optional<ErrorFieldRole> roleOf(String fieldName) {
        String lastWord = lastWord(fieldName);
        return RULES.stream().filter(rule -> rule.matches(lastWord)).map(RoleRule::role).findFirst();
    }

    /**
     * The last word of a field name, in lower case: {@code errorCode} gives {@code code}.
     *
     * @param fieldName the JSON key
     * @return the last word, or an empty string for a key without letters or digits
     */
    static String lastWord(String fieldName) {
        List<String> words = words(fieldName);
        return words.isEmpty() ? "" : words.getLast();
    }

    /**
     * Tells whether a code field carries the HTTP status or a response code of the transport instead of the error
     * ({@code statusCode}, {@code httpCode}), which says nothing the status line does not.
     *
     * @param fieldName the JSON key
     * @return {@code true} if the key is qualified by status, http or response
     */
    public static boolean isTransportCode(String fieldName) {
        return words(fieldName).stream().anyMatch(TRANSPORT_WORDS::contains);
    }

    private static List<String> words(String fieldName) {
        return WORD_BOUNDARY.splitAsStream(fieldName).filter(word -> !word.isEmpty()).map(word -> word.toLowerCase(Locale.ROOT)).toList();
    }

    private record RoleRule(ErrorFieldRole role, Set<String> words, Set<String> compoundSuffixes) {

        boolean matches(String lastWord) {
            return words.contains(lastWord) || compoundSuffixes.stream().anyMatch(lastWord::endsWith);
        }
    }
}
