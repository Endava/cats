package com.endava.cats.command.triage;

import com.endava.cats.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Renders synthesized rules as a {@code --correlations} file, grouping rules per path ({@code rules:}) and merging
 * rules sharing the same {@code when:} into a single {@code set:}/{@code omit:} block. Scalars are emitted as JSON
 * (valid YAML), so placeholders keep their types and user-controlled text cannot break the document.
 */
final class CorrelationsYamlRenderer {
    private static final String EMPTY = "# No cross-field correlation rules needed\ncorrelations: []\n";
    private static final String HEADER = """
            # Auto-synthesized by `cats triage`. Review every rule before passing it to --correlations:
            #   - replace placeholders with valid domain values (numbers, enum literals, existing IDs)
            #   - confirm each `when:` trigger exists in the generated payload: cats generate -c <spec> -p <path> -X <method>
            correlations:
            """;
    private static final String UNCONDITIONAL = "";

    private CorrelationsYamlRenderer() {
    }

    static String render(List<SynthesizedCorrelationRule> rules) {
        if (rules.isEmpty()) {
            return EMPTY;
        }
        StringBuilder yaml = new StringBuilder(HEADER);
        group(rules).forEach((path, groups) -> appendPath(yaml, path, groups.values()));
        return yaml.toString();
    }

    private static Map<String, Map<String, RuleGroup>> group(List<SynthesizedCorrelationRule> rules) {
        Map<String, Map<String, RuleGroup>> byPath = new LinkedHashMap<>();
        rules.forEach(rule -> byPath.computeIfAbsent(rule.path(), _ -> new LinkedHashMap<>())
                .computeIfAbsent(Objects.requireNonNullElse(rule.when(), UNCONDITIONAL), _ -> new RuleGroup(rule.when()))
                .add(rule));
        return byPath;
    }

    private static void appendPath(StringBuilder yaml, String path, Iterable<RuleGroup> groups) {
        yaml.append("  - paths:\n      - ").append(scalar(path)).append("\n    rules:\n");
        groups.forEach(group -> appendRule(yaml, group));
    }

    private static void appendRule(StringBuilder yaml, RuleGroup group) {
        List<String> lines = new ArrayList<>();
        if (group.when() != null) {
            lines.add("when: " + scalar(group.when()));
        }
        group.sources().forEach(source -> lines.add("# from: " + ReportText.singleLine(source)));
        if (!group.set().isEmpty()) {
            lines.add("set:");
            group.set().forEach((field, value) -> lines.add("  " + key(field) + ": " + JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(value)));
        }
        List<String> omit = group.effectiveOmit();
        if (!omit.isEmpty()) {
            lines.add("omit:");
            omit.forEach(field -> lines.add("  - " + scalar(field)));
        }
        for (int i = 0; i < lines.size(); i++) {
            yaml.append(i == 0 ? "      - " : "        ").append(lines.get(i)).append('\n');
        }
    }

    private static String key(String field) {
        return FieldNames.isSimpleYamlKey(field) ? field : scalar(field);
    }

    private static String scalar(String text) {
        return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(text);
    }

    private record RuleGroup(String when, Map<String, Object> set, Set<String> omit, Set<String> sources) {
        RuleGroup(String when) {
            this(when, new LinkedHashMap<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
        }

        void add(SynthesizedCorrelationRule rule) {
            sources.add(rule.sourceError());
            if (FailureIntent.REMOVE.name().equals(rule.action())) {
                omit.add(rule.targetField());
            } else {
                set.putIfAbsent(rule.targetField(), rule.value());
            }
        }

        /**
         * Drops {@code omit:} entries contradicting a {@code set:} of the same field under the same trigger.
         */
        List<String> effectiveOmit() {
            return omit.stream().filter(field -> !set.containsKey(field)).toList();
        }
    }
}
