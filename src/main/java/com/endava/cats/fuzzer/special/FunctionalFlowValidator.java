package com.endava.cats.fuzzer.special;

import com.endava.cats.exception.CatsException;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FieldCorrelationRule;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowExpectation;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowStep;
import com.endava.cats.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates functional flow documents before execution: detects undefined variable references,
 * cyclic global variables, references to variables or step outputs produced by tag-excluded steps,
 * and broken {@code $prev} references.
 */
public final class FunctionalFlowValidator {
    static final Pattern VAR_IDENTIFIER_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    static final Pattern OPTIONAL_VAR_IDENTIFIER_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\?}");
    private static final Pattern ANY_VAR_IDENTIFIER_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\??}");
    private static final Pattern STEP_TOKEN_PATTERN = Pattern.compile(
            "\\$\\{((?:steps|prev)\\.[^}]+)}|\\$((?:steps|prev)\\.[A-Za-z0-9_.#\\[\\]-]+)");

    private FunctionalFlowValidator() {
        // utility class
    }

    public static void validate(FunctionalFlowDocument document,
                                List<FunctionalFlowStep> selectedSteps,
                                Map<String, String> environmentVariables) {
        if (document == null) {
            return;
        }
        ValidationRun run = ValidationRun.create(document, selectedSteps, environmentVariables);
        int executedStepIndex = 1;
        for (FunctionalFlowStep step : selectedSteps) {
            run.validateStep(document, step, executedStepIndex++);
        }
        run.throwIfErrorsPresent();
    }

    static List<String> orderGlobalVariableKeys(Map<String, Object> rawVars) {
        if (rawVars == null || rawVars.isEmpty()) {
            return List.of();
        }
        List<String> ordered = new ArrayList<>(rawVars.size());
        Set<String> visited = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (String key : rawVars.keySet()) {
            visitGlobalVariableKey(key, rawVars, visited, visiting, ordered);
        }
        return ordered;
    }

    private static void visitGlobalVariableKey(String key, Map<String, Object> rawVars,
                                               Set<String> visited, Set<String> visiting, List<String> ordered) {
        if (visited.contains(key) || !visiting.add(key)) {
            return;
        }
        for (String dependency : extractAllReferencedVarIdentifiers(rawVars.get(key))) {
            if (rawVars.containsKey(dependency) && !dependency.equals(key)) {
                visitGlobalVariableKey(dependency, rawVars, visited, visiting, ordered);
            }
        }
        visiting.remove(key);
        visited.add(key);
        ordered.add(key);
    }

    static Set<String> extractReferencedVarIdentifiers(Object value) {
        return extractIdentifiersMatching(value, VAR_IDENTIFIER_PATTERN);
    }

    static Set<String> extractAllReferencedVarIdentifiers(Object value) {
        return extractIdentifiersMatching(value, ANY_VAR_IDENTIFIER_PATTERN);
    }

    private static Set<String> extractIdentifiersMatching(Object value, Pattern pattern) {
        List<String> strings = new ArrayList<>();
        collectNestedStrings(value, strings);
        Set<String> refs = new LinkedHashSet<>();
        for (String text : strings) {
            if (!text.contains("${")) {
                continue;
            }
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                refs.add(matcher.group(1));
            }
        }
        return refs;
    }

    private static List<String> collectRequestExpressions(FunctionalFlowDocument document, FunctionalFlowStep step) {
        List<String> expressions = new ArrayList<>();
        addIfText(step.rawTarget(), expressions);
        Optional.ofNullable(step.targetPaths()).orElse(List.of()).forEach(path -> addIfText(path, expressions));
        collectNestedStrings(step.path(), expressions);
        collectNestedStrings(step.query(), expressions);
        collectEffectiveHeaders(document.headers(), step, expressions, document.variables());
        collectNestedStrings(step.body(), expressions);
        Optional.ofNullable(step.omit()).orElse(List.of()).forEach(entry -> addIfText(entry, expressions));
        collectCorrelationStrings(matchingDocumentCorrelations(document.correlate(), step), expressions);
        collectCorrelationStrings(step.correlate(), expressions);
        return expressions;
    }

    private static List<FieldCorrelationRule> matchingDocumentCorrelations(List<FieldCorrelationRule> rules,
                                                                           FunctionalFlowStep step) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        String rawPath = stripQuery(step.rawTarget());
        return rules.stream()
                .filter(rule -> matchesCorrelationRuleForStep(rawPath, step.method(), rule.paths()))
                .toList();
    }

    private static boolean matchesCorrelationRuleForStep(String rawPath, HttpMethod method,
                                                         List<String> patterns) {
        if (FieldCorrelationEvaluator.matchesPathPattern(rawPath, method, patterns)) {
            return true;
        }
        if (rawPath == null || patterns == null) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> matchesTemplatePath(rawPath, method, pattern));
    }

    private static boolean matchesTemplatePath(String rawPath, HttpMethod method, String rawPattern) {
        if (StringUtils.isBlank(rawPattern)) {
            return false;
        }
        String trimmed = rawPattern.trim();
        String pathPattern = trimmed;
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx > 0) {
            String verb = trimmed.substring(0, spaceIdx).trim();
            if (method != null && !verb.equalsIgnoreCase(method.name())) {
                return false;
            }
            pathPattern = trimmed.substring(spaceIdx + 1).trim();
        }
        String[] rawSegs = StringUtils.strip(rawPath, "/").split("/", -1);
        String[] patSegs = StringUtils.strip(pathPattern, "/").split("/", -1);
        if (rawSegs.length != patSegs.length) {
            return false;
        }
        for (int i = 0; i < rawSegs.length; i++) {
            boolean isPlaceholder = (patSegs[i].startsWith("{") && patSegs[i].endsWith("}"))
                    || (rawSegs[i].startsWith("${") && rawSegs[i].endsWith("}"));
            if (!isPlaceholder && !rawSegs[i].equalsIgnoreCase(patSegs[i])) {
                return false;
            }
        }
        return true;
    }

    private static String stripQuery(String rawTarget) {
        if (rawTarget == null) {
            return null;
        }
        int qIdx = rawTarget.indexOf('?');
        return qIdx >= 0 ? rawTarget.substring(0, qIdx) : rawTarget;
    }

    private static List<String> collectExpectationExpressions(FunctionalFlowExpectation expect) {
        if (expect == null) {
            return List.of();
        }
        List<String> expressions = new ArrayList<>();
        collectNestedStrings(expect.fields(), expressions);
        collectNestedStrings(expect.headers(), expressions);
        Optional.ofNullable(expect.that()).orElse(List.of()).forEach(expr -> addIfText(expr, expressions));
        return expressions;
    }

    private static void collectEffectiveHeaders(Map<String, Object> globalHeaders, FunctionalFlowStep step,
                                                List<String> target, Map<String, Object> globalVars) {
        Set<String> overriddenOrOmitted = collectOverriddenHeaderNames(step, globalVars);
        if (globalHeaders != null) {
            globalHeaders.forEach((name, val) -> {
                if (!overriddenOrOmitted.contains(name.toLowerCase(Locale.ROOT))) {
                    collectNestedStrings(val, target);
                }
            });
        }
        collectNestedStrings(step.headers(), target);
    }

    private static Set<String> collectOverriddenHeaderNames(FunctionalFlowStep step, Map<String, Object> globalVars) {
        Set<String> names = new LinkedHashSet<>();
        if (step.headers() != null) {
            step.headers().keySet().forEach(k -> names.add(k.toLowerCase(Locale.ROOT)));
        }
        extractStandaloneIdentifier(step.headersRef())
                .map(varName -> resolveStaticVariable(varName, globalVars))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .ifPresent(map -> map.keySet().forEach(k -> names.add(String.valueOf(k).toLowerCase(Locale.ROOT))));
        for (String omitEntry : Optional.ofNullable(step.omit()).orElse(List.of())) {
            addOmittedHeaderNames(omitEntry, globalVars, names);
        }
        return names;
    }

    private static void addOmittedHeaderNames(String omitEntry, Map<String, Object> globalVars, Set<String> names) {
        if (StringUtils.isBlank(omitEntry)) {
            return;
        }
        Optional<String> varName = extractStandaloneIdentifier(omitEntry);
        if (varName.isPresent() && globalVars != null && globalVars.containsKey(varName.get())) {
            Object staticVal = resolveStaticVariable(varName.get(), globalVars);
            if (staticVal instanceof List<?> list) {
                list.stream().filter(item -> item != null).forEach(item -> addSingleOmitHeaderName(String.valueOf(item), names));
            } else if (staticVal instanceof String str) {
                addSingleOmitHeaderName(str, names);
            }
            return;
        }
        addSingleOmitHeaderName(omitEntry, names);
    }

    private static void addSingleOmitHeaderName(String omitEntry, Set<String> names) {
        if (StringUtils.isBlank(omitEntry)) {
            return;
        }
        String lower = omitEntry.trim().toLowerCase(Locale.ROOT);
        names.add((lower.startsWith("headers.") || lower.startsWith("header."))
                ? lower.substring(lower.indexOf('.') + 1).trim()
                : lower);
    }

    static Optional<String> extractStandaloneIdentifier(String rawExpr) {
        if (rawExpr == null) {
            return Optional.empty();
        }
        String trimmed = rawExpr.trim();
        Matcher matcher = VAR_IDENTIFIER_PATTERN.matcher(trimmed);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    static Optional<String> extractStandaloneOptionalIdentifier(String rawExpr) {
        if (rawExpr == null) {
            return Optional.empty();
        }
        String trimmed = rawExpr.trim();
        Matcher matcher = OPTIONAL_VAR_IDENTIFIER_PATTERN.matcher(trimmed);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static Object resolveStaticVariable(String varName, Map<String, Object> globalVars) {
        Set<String> seen = new LinkedHashSet<>();
        String current = varName;
        while (globalVars != null && globalVars.containsKey(current) && seen.add(current)) {
            Object val = globalVars.get(current);
            if (val instanceof String str) {
                Optional<String> next = extractStandaloneIdentifier(str)
                        .or(() -> extractStandaloneOptionalIdentifier(str));
                if (next.isPresent() && globalVars.containsKey(next.get())) {
                    current = next.get();
                    continue;
                }
            }
            return val;
        }
        return null;
    }

    private static void collectCorrelationStrings(List<FieldCorrelationRule> rules, List<String> target) {
        if (rules == null) {
            return;
        }
        for (FieldCorrelationRule rule : rules) {
            addIfText(rule.when(), target);
            collectNestedStrings(rule.set(), target);
        }
    }

    private static Set<String> withGuaranteedForEachKeys(Set<String> baseVars, List<Map<String, Object>> forEachRows) {
        if (forEachRows == null || forEachRows.isEmpty()) {
            return baseVars;
        }
        Set<String> commonRowKeys = new LinkedHashSet<>(forEachRows.getFirst().keySet());
        for (int i = 1; i < forEachRows.size(); i++) {
            commonRowKeys.retainAll(forEachRows.get(i).keySet());
        }
        Set<String> combined = new LinkedHashSet<>(baseVars);
        combined.addAll(commonRowKeys);
        return combined;
    }

    private static void collectNestedStrings(Object value, List<String> target) {
        switch (value) {
            case String str -> target.add(str);
            case Map<?, ?> map -> map.values().forEach(v -> collectNestedStrings(v, target));
            case List<?> list -> list.forEach(item -> collectNestedStrings(item, target));
            case null, default -> {
                // non-string scalar or null
            }
        }
    }

    private static void addIfText(String value, List<String> target) {
        if (StringUtils.isNotBlank(value)) {
            target.add(value);
        }
    }

    private record SelectedProducer(int executedStepIndex, String stepName) {
    }

    private static final class ValidationRun {
        private final ValidationIndex index;
        private final Set<String> availableVars;
        private final Set<String> availableStepKeys = new LinkedHashSet<>();
        private final List<String> errors = new ArrayList<>();

        private ValidationRun(ValidationIndex index, Set<String> availableVars) {
            this.index = index;
            this.availableVars = availableVars;
        }

        static ValidationRun create(FunctionalFlowDocument document, List<FunctionalFlowStep> selectedSteps,
                                    Map<String, String> environmentVariables) {
            ValidationIndex idx = ValidationIndex.build(document, selectedSteps);
            Set<String> vars = new LinkedHashSet<>();
            if (environmentVariables != null) {
                vars.addAll(environmentVariables.keySet());
            }
            ValidationRun run = new ValidationRun(idx, vars);
            run.validateGlobalVariables(document.variables());
            run.validateDocumentHeadersRef(document);
            return run;
        }

        private void validateDocumentHeadersRef(FunctionalFlowDocument document) {
            if (StringUtils.isBlank(document.headersRef())) {
                return;
            }
            validateHeadersRefExpression("Document", document.headersRef(), availableVars, document.variables(), null, 0);
        }

        void validateGlobalVariables(Map<String, Object> rawVars) {
            if (rawVars == null || rawVars.isEmpty()) {
                return;
            }
            availableVars.addAll(rawVars.keySet());
            rawVars.forEach(this::checkGlobalVariableReferences);
            detectGlobalVariableCycles(rawVars);
        }

        private void checkGlobalVariableReferences(String varKey, Object value) {
            for (String referenced : extractReferencedVarIdentifiers(value)) {
                if (!availableVars.contains(referenced)) {
                    errors.add("Variable '%s' references undefined variable '${%s}'".formatted(varKey, referenced));
                }
            }
        }

        private void detectGlobalVariableCycles(Map<String, Object> rawVars) {
            Set<String> visited = new LinkedHashSet<>();
            Set<String> active = new LinkedHashSet<>();
            for (String key : rawVars.keySet()) {
                detectVarCycleFrom(key, rawVars, visited, active);
            }
        }

        private void detectVarCycleFrom(String key, Map<String, Object> rawVars,
                                        Set<String> visited, Set<String> active) {
            if (visited.contains(key)) {
                return;
            }
            if (!active.add(key)) {
                List<String> cycle = active.stream()
                        .dropWhile(v -> !v.equals(key))
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
                cycle.add(key);
                errors.add("Cyclic variable reference detected: %s".formatted(String.join(" -> ", cycle)));
                return;
            }
            for (String dep : extractAllReferencedVarIdentifiers(rawVars.get(key))) {
                if (rawVars.containsKey(dep)) {
                    detectVarCycleFrom(dep, rawVars, visited, active);
                }
            }
            active.remove(key);
            visited.add(key);
        }

        void validateStep(FunctionalFlowDocument document, FunctionalFlowStep step, int executedStepIndex) {
            validateForEachRows(step, executedStepIndex);
            Set<String> requestVars = withGuaranteedForEachKeys(availableVars, step.forEach());
            if (StringUtils.isNotBlank(step.headersRef())) {
                validateHeadersRefExpression("Step '" + step.name() + "'", step.headersRef(),
                        requestVars, document.variables(), step, executedStepIndex);
            }
            for (String expr : collectRequestExpressions(document, step)) {
                validateExpression(step, expr, executedStepIndex, requestVars, true);
            }

            availableStepKeys.addAll(FunctionalFlowFuzzer.stepKeys(step.name(), executedStepIndex));
            validateSaveStepReferences(step, executedStepIndex);
            availableVars.addAll(step.save().keySet());

            Set<String> expectVars = withGuaranteedForEachKeys(availableVars, step.forEach());
            for (String expr : collectExpectationExpressions(step.expect())) {
                validateExpression(step, expr, executedStepIndex, expectVars, false);
            }
        }

        private void validateHeadersRefExpression(String owner, String headersRef, Set<String> scopeVars,
                                                  Map<String, Object> globalVars, FunctionalFlowStep step,
                                                  int executedStepIndex) {
            Optional<String> requiredVar = extractStandaloneIdentifier(headersRef);
            Optional<String> optionalVar = requiredVar.isPresent() ? Optional.empty() : extractStandaloneOptionalIdentifier(headersRef);
            if (requiredVar.isEmpty() && optionalVar.isEmpty()) {
                errors.add("%s 'headers' must be a map or a standalone '${variable}' reference, got '%s'"
                        .formatted(owner, headersRef));
                return;
            }
            String name = requiredVar.orElseGet(optionalVar::orElseThrow);
            if (!scopeVars.contains(name)) {
                if (optionalVar.isEmpty()) {
                    errors.add(step == null
                            ? "%s 'headers' references undefined variable '${%s}'".formatted(owner, name)
                            : describeMissingVariable(step, name, executedStepIndex));
                }
                return;
            }
            if (globalVars != null && globalVars.containsKey(name) && !isOverriddenByForEach(step, name)) {
                Object staticVal = resolveStaticVariable(name, globalVars);
                if (staticVal != null && !(staticVal instanceof Map<?, ?>)) {
                    errors.add("%s 'headers' variable '${%s}' must be a map/object, got %s"
                            .formatted(owner, name, staticVal.getClass().getSimpleName()));
                }
            }
        }

        private static boolean isOverriddenByForEach(FunctionalFlowStep step, String varName) {
            return step != null && step.forEach() != null
                    && step.forEach().stream().anyMatch(row -> row.containsKey(varName));
        }

        private void validateForEachRows(FunctionalFlowStep step, int executedStepIndex) {
            if (step.forEach() == null) {
                return;
            }
            for (Map<String, Object> row : step.forEach()) {
                Set<String> rowVars = new LinkedHashSet<>(availableVars);
                row.forEach((key, value) -> {
                    List<String> rowExpressions = new ArrayList<>();
                    collectNestedStrings(value, rowExpressions);
                    for (String expr : rowExpressions) {
                        validateExpression(step, expr, executedStepIndex, rowVars, true);
                    }
                    rowVars.add(key);
                });
            }
        }

        void throwIfErrorsPresent() {
            if (!errors.isEmpty()) {
                throw new CatsException("Functional flow validation failed:%n - %s"
                        .formatted(String.join(System.lineSeparator() + " - ", errors)));
            }
        }

        private void validateSaveStepReferences(FunctionalFlowStep step, int executedStepIndex) {
            for (String selector : step.save().values()) {
                if (selector != null && (selector.startsWith("steps.") || selector.startsWith("$steps.")
                        || selector.startsWith("prev.") || selector.startsWith("$prev."))) {
                    String token = selector.startsWith("$") ? selector.substring(1) : selector;
                    validateStepToken(step, token, executedStepIndex, false);
                }
            }
        }

        private void validateExpression(FunctionalFlowStep step, String expression,
                                        int executedStepIndex, Set<String> stepVars, boolean checkPrev) {
            if (StringUtils.isBlank(expression)) {
                return;
            }
            validateStepReferences(step, expression, executedStepIndex, checkPrev);
            validateVariableIdentifiers(step, expression, executedStepIndex, stepVars);
        }

        private void validateStepReferences(FunctionalFlowStep step, String expression,
                                            int executedStepIndex, boolean checkPrev) {
            if (!expression.contains("steps.") && !expression.contains("prev.")) {
                return;
            }
            Matcher matcher = STEP_TOKEN_PATTERN.matcher(expression);
            while (matcher.find()) {
                String token = matcher.group(matcher.group(1) != null ? 1 : 2);
                validateStepToken(step, token, executedStepIndex, checkPrev);
            }
        }

        private void validateStepToken(FunctionalFlowStep step, String token,
                                       int executedStepIndex, boolean checkPrev) {
            if (token.startsWith("prev.")) {
                if (checkPrev) {
                    validatePrevReference(step, executedStepIndex);
                }
            } else if (token.startsWith("steps.")) {
                validateNamedStepReference(step, token);
            }
        }

        private void validatePrevReference(FunctionalFlowStep step, int executedStepIndex) {
            if (executedStepIndex == 1) {
                errors.add("Step '%s' references '$prev' but no preceding step will execute".formatted(step.name()));
                return;
            }
            int originalPos = index.originalIndices().getOrDefault(step, -1);
            if (originalPos > 0) {
                FunctionalFlowStep preceding = index.allSteps().get(originalPos - 1);
                if (!index.selectedSteps().contains(preceding)) {
                    errors.add("Step '%s' references '$prev', but preceding step '%s' was excluded by step tag filters"
                            .formatted(step.name(), preceding.name()));
                }
            }
        }

        private void validateNamedStepReference(FunctionalFlowStep step, String token) {
            String remainder = token.substring("steps.".length());
            int dotIdx = remainder.indexOf('.');
            String stepKey = dotIdx > 0 ? remainder.substring(0, dotIdx) : remainder;
            if (availableStepKeys.contains(stepKey)) {
                return;
            }
            String excludedName = index.excludedStepKeys().get(stepKey);
            if (excludedName != null) {
                errors.add("Step '%s' references 'steps.%s' from step '%s' which was excluded by step tag filters"
                        .formatted(step.name(), stepKey, excludedName));
            } else {
                errors.add("Step '%s' references unknown or later step 'steps.%s'".formatted(step.name(), stepKey));
            }
        }

        private void validateVariableIdentifiers(FunctionalFlowStep step, String expression,
                                                 int executedStepIndex, Set<String> stepVars) {
            if (!expression.contains("${")) {
                return;
            }
            Matcher matcher = VAR_IDENTIFIER_PATTERN.matcher(expression);
            while (matcher.find()) {
                String varName = matcher.group(1);
                if (!stepVars.contains(varName)) {
                    errors.add(describeMissingVariable(step, varName, executedStepIndex));
                }
            }
        }

        private String describeMissingVariable(FunctionalFlowStep step, String varName, int executedStepIndex) {
            String excludedProducer = index.excludedStepSaves().get(varName);
            if (excludedProducer != null) {
                return "Step '%s' requires variable '${%s}' which is saved by step '%s' (excluded by step tag filters)"
                        .formatted(step.name(), varName, excludedProducer);
            }
            String laterProducer = index.findLaterSelectedProducer(varName, executedStepIndex);
            if (laterProducer != null) {
                return "Step '%s' uses variable '${%s}' before it is saved by later step '%s'"
                        .formatted(step.name(), varName, laterProducer);
            }
            return "Step '%s' references undefined variable '${%s}'".formatted(step.name(), varName);
        }
    }

    private record ValidationIndex(
            List<FunctionalFlowStep> allSteps,
            Set<FunctionalFlowStep> selectedSteps,
            IdentityHashMap<FunctionalFlowStep, Integer> originalIndices,
            Map<String, String> excludedStepSaves,
            Map<String, String> excludedStepKeys,
            Map<String, List<SelectedProducer>> selectedStepSaves) {

        static ValidationIndex build(FunctionalFlowDocument document, List<FunctionalFlowStep> selected) {
            List<FunctionalFlowStep> allSteps = document.steps();
            Set<FunctionalFlowStep> selectedSet = Collections.newSetFromMap(new IdentityHashMap<>());
            selectedSet.addAll(selected);

            IdentityHashMap<FunctionalFlowStep, Integer> originalIndices = new IdentityHashMap<>();
            Map<String, String> excludedSaves = new LinkedHashMap<>();
            Map<String, String> excludedKeys = new LinkedHashMap<>();

            for (int i = 0; i < allSteps.size(); i++) {
                FunctionalFlowStep step = allSteps.get(i);
                originalIndices.put(step, i);
                if (!selectedSet.contains(step)) {
                    step.save().keySet().forEach(varName -> excludedSaves.putIfAbsent(varName, step.name()));
                    FunctionalFlowFuzzer.stepKeys(step.name(), i + 1)
                            .forEach(key -> excludedKeys.putIfAbsent(key, step.name()));
                }
            }

            Map<String, List<SelectedProducer>> selectedSaves = new LinkedHashMap<>();
            for (int i = 0; i < selected.size(); i++) {
                FunctionalFlowStep step = selected.get(i);
                SelectedProducer producer = new SelectedProducer(i + 1, step.name());
                step.save().keySet().forEach(varName ->
                        selectedSaves.computeIfAbsent(varName, _ -> new ArrayList<>()).add(producer));
            }
            return new ValidationIndex(allSteps, selectedSet, originalIndices, excludedSaves, excludedKeys, selectedSaves);
        }

        String findLaterSelectedProducer(String varName, int currentStepIndex) {
            return selectedStepSaves.getOrDefault(varName, List.of()).stream()
                    .filter(p -> p.executedStepIndex() >= currentStepIndex)
                    .map(SelectedProducer::stepName)
                    .findFirst()
                    .orElse(null);
        }
    }
}
