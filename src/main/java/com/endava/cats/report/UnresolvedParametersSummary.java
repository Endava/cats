package com.endava.cats.report;

import com.endava.cats.io.RuntimeResourcePool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders the end-of-run summary of identifier parameters that could not be resolved from earlier responses,
 * together with a suggested {@code --refData} file content.
 */
public final class UnresolvedParametersSummary {
    static final int MAX_OPERATIONS = 20;

    private UnresolvedParametersSummary() {
        //ntd
    }

    /**
     * Renders the summary lines. Returns an empty list when there is nothing to report.
     *
     * @param operations operations with unresolved identifier parameters
     * @return summary lines to be printed
     */
    public static List<String> render(List<RuntimeResourcePool.UnresolvedOperation> operations) {
        if (operations.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add("%d operation(s) never succeeded with unmodified requests and depend on identifiers CATS could not resolve at runtime:"
                .formatted(operations.size()));
        operations.stream().limit(MAX_OPERATIONS).forEach(operation -> {
            RuntimeResourcePool.OperationOutcome outcome = operation.outcome();
            lines.add("  %s %s (%s)".formatted(outcome.method(), outcome.path(), describe(outcome)));
            operation.parameters().forEach(parameter -> lines.add("    - %s [%s]: %s".formatted(
                    refDataKey(parameter), parameter.target().location().name().toLowerCase(Locale.ROOT), describe(parameter))));
        });
        if (operations.size() > MAX_OPERATIONS) {
            lines.add("  ... and %d more operation(s)".formatted(operations.size() - MAX_OPERATIONS));
        }
        lines.add("Supply valid values using --refData (or --urlParams for path parameters). Suggested --refData file content:");
        lines.addAll(refDataSuggestion(operations));
        return List.copyOf(lines);
    }

    private static String describe(RuntimeResourcePool.UnresolvedParameter parameter) {
        String reason = switch (parameter.reason()) {
            case NO_VALUE_CAPTURED -> "no matching value was captured from earlier successful responses";
            case CAPTURED_VALUES_NOT_FOUND -> "values reused from earlier responses returned 404/410";
        };
        RuntimeResourcePool.OperationOutcome producer = parameter.producer();
        if (producer == null) {
            return reason;
        }
        return "%s; producer %s %s: %s".formatted(reason, producer.method(), producer.path(), describe(producer));
    }

    private static String describe(RuntimeResourcePool.OperationOutcome outcome) {
        String codes = outcome.statusCodes().entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .map(entry -> entry.getKey() + "x" + entry.getValue())
                .collect(Collectors.joining(", "));
        return "%d/%d successful, responses: %s".formatted(outcome.successes(), outcome.requests(), codes);
    }

    private static List<String> refDataSuggestion(List<RuntimeResourcePool.UnresolvedOperation> operations) {
        Map<String, Set<String>> fieldsByPath = new LinkedHashMap<>();
        operations.forEach(operation -> operation.parameters().forEach(parameter ->
                fieldsByPath.computeIfAbsent(operation.outcome().path(), _ -> new LinkedHashSet<>()).add(refDataKey(parameter))));
        List<String> lines = new ArrayList<>();
        fieldsByPath.forEach((path, fields) -> {
            lines.add("\"%s\":".formatted(escape(path)));
            fields.forEach(field -> lines.add("  \"%s\": \"<valid %s>\"".formatted(escape(field), escape(leafName(field)))));
        });
        return lines;
    }

    private static String refDataKey(RuntimeResourcePool.UnresolvedParameter parameter) {
        return parameter.target().name().replace('#', '.');
    }

    private static String leafName(String field) {
        return field.substring(field.lastIndexOf('.') + 1);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
