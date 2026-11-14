package com.endava.cats.report.error.fields;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Chooses the error type among the fields of a JSON error body. Fields are ranked, the lowest rank wins and the
 * first one in document order breaks ties:
 * <ol>
 *     <li>the well-known fields, in the order {@code exception, errorCode, code, title, error, type};</li>
 *     <li>other fields whose name ends in {@code code} ({@code reasonCode}, {@code fault_code}), right after
 *     {@code code}, because they are error codes;</li>
 *     <li>other fields whose name ends in {@code type} or {@code kind}, after {@code type};</li>
 * </ol>
 * Codes of the transport ({@code statusCode}, {@code httpCode}) never are the error type: they repeat the status line.
 * {@code title} and {@code error} hold free text, every other chosen field a code or a type.
 */
public final class ErrorTypeFields {
    private static final Map<String, Integer> WELL_KNOWN_RANKS = Map.of("exception", 0, "errorCode", 10, "code", 20,
            "title", 30, "error", 40, "type", 50);
    private static final Set<String> FREE_TEXT_FIELDS = Set.of("title", "error");
    private static final List<String> TITLE_FIELDS = List.of("title", "error", "code", "errorCode");
    private static final int NO_RANK = -1;
    private static final int CODE_RANK = 25;
    private static final int KIND_RANK = 55;

    private ErrorTypeFields() {
    }

    /**
     * An error type and whether it was read from a code or type field.
     *
     * @param value         the error type, or empty
     * @param fromCodeField {@code true} unless the value is free text such as a title
     */
    public record ErrorType(String value, boolean fromCodeField) {
        static final ErrorType EMPTY = new ErrorType("", false);
    }

    private record Candidate(String field, String value, int rank) {
    }

    /**
     * Chooses the error type of a JSON error object. A problem-details {@code type} URI or {@code about:blank} is
     * replaced by the title, which stays the same across occurrences of the problem.
     *
     * @param obj   the error object
     * @param roles the field roles
     * @return the error type, {@link ErrorType#EMPTY} when the object has none
     */
    public static ErrorType choose(JsonObject obj, ErrorFieldRoles roles) {
        return obj.entrySet().stream().map(entry -> candidate(entry.getKey(), entry.getValue(), roles)).flatMap(Optional::stream)
                .min(Comparator.comparingInt(Candidate::rank))
                .map(candidate -> withTitleForProblemTypes(candidate, obj))
                .orElse(ErrorType.EMPTY);
    }

    private static Optional<Candidate> candidate(String field, JsonElement value, ErrorFieldRoles roles) {
        int rank = rank(field, roles);
        String text = value.isJsonPrimitive() ? value.getAsString().strip() : "";
        return rank == NO_RANK || text.isEmpty() ? Optional.empty() : Optional.of(new Candidate(field, text, rank));
    }

    private static int rank(String field, ErrorFieldRoles roles) {
        if (roles.has(field, ErrorFieldRole.VARIABLE)) {
            return NO_RANK;
        }
        Integer wellKnown = WELL_KNOWN_RANKS.get(field);
        return wellKnown != null ? wellKnown : roles.has(field, ErrorFieldRole.TYPE) ? roleRank(field) : NO_RANK;
    }

    private static int roleRank(String field) {
        if (ErrorFieldRoleResolver.isTransportCode(field)) {
            return NO_RANK;
        }
        return ErrorFieldRoleResolver.lastWord(field).startsWith("code") ? CODE_RANK : KIND_RANK;
    }

    private static ErrorType withTitleForProblemTypes(Candidate candidate, JsonObject obj) {
        String title = isProblemTypeUri(candidate.value()) ? JsonErrorFields.firstStringProperty(obj, TITLE_FIELDS) : "";
        return title.isEmpty() ? new ErrorType(candidate.value(), !FREE_TEXT_FIELDS.contains(candidate.field()))
                : new ErrorType(title, false);
    }

    private static boolean isProblemTypeUri(String value) {
        return value.startsWith("http://") || value.startsWith("https://") || "about:blank".equalsIgnoreCase(value);
    }
}
