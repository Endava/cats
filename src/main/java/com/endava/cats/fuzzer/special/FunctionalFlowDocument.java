package com.endava.cats.fuzzer.special;

import com.endava.cats.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable model representing a functional or security flow YAML document with global variables,
 * cross-field correlation rules, and sequential HTTP steps.
 */
public record FunctionalFlowDocument(
        Map<String, Object> variables,
        Map<String, Object> headers,
        String headersRef,
        List<FieldCorrelationRule> correlate,
        List<FunctionalFlowStep> steps,
        List<String> stepTags,
        List<String> skipStepTags) {

    public FunctionalFlowDocument {
        stepTags = stepTags == null ? List.of() : List.copyOf(stepTags);
        skipStepTags = skipStepTags == null ? List.of() : List.copyOf(skipStepTags);
    }

    public FunctionalFlowDocument(
            Map<String, Object> variables,
            Map<String, Object> headers,
            List<FieldCorrelationRule> correlate,
            List<FunctionalFlowStep> steps) {
        this(variables, headers, null, correlate, steps, List.of(), List.of());
    }

    public FunctionalFlowDocument(
            Map<String, Object> variables,
            List<FieldCorrelationRule> correlate,
            List<FunctionalFlowStep> steps) {
        this(variables, Map.of(), null, correlate, steps, List.of(), List.of());
    }

    public enum BasePayloadMode {
        MERGE,
        ONLY_REQUIRED,
        EMPTY;

        public static BasePayloadMode fromString(String value) {
            if (value == null || value.isBlank()) {
                return MERGE;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "")) {
                case "onlyrequired", "required", "requiredonly" -> ONLY_REQUIRED;
                case "empty", "none", "raw" -> EMPTY;
                default -> MERGE;
            };
        }
    }

    public enum FuzzMode {
        SINGLE,
        CROSS;

        public static FuzzMode fromString(String value) {
            if (value == null || value.isBlank()) {
                return SINGLE;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "")) {
                case "cross", "all", "simultaneous", "combined", "multi" -> CROSS;
                default -> SINGLE;
            };
        }
    }

    public record FieldCorrelationRule(
            List<String> paths,
            String when,
            Map<String, Object> set,
            List<String> omit) {
    }

    public record StepFuzzConfig(
            List<String> fields,
            List<String> fieldTypes,
            List<String> payloads,
            List<String> payloadsFiles,
            FuzzMode mode) {

        public boolean isConfigured() {
            return !fields.isEmpty() || !fieldTypes.isEmpty() || !payloads.isEmpty() || !payloadsFiles.isEmpty();
        }

        public boolean hasPayloadSource() {
            return !payloads.isEmpty() || !payloadsFiles.isEmpty();
        }

        public boolean hasTargets() {
            return !fields.isEmpty() || !fieldTypes.isEmpty();
        }
    }

    public record FunctionalFlowExpectation(
            List<String> status,
            Map<String, Object> fields,
            Map<String, String> headers,
            List<String> bodyContains,
            List<String> bodyNotContains,
            List<String> that,
            boolean matchesSchema,
            Long maxResponseTimeMs) {
    }

    public record PollConfig(
            int maxAttempts,
            long intervalMs) {
    }

    public record FunctionalFlowStep(
            String name,
            String description,
            HttpMethod method,
            boolean allMethods,
            String rawTarget,
            List<String> targetPaths,
            Map<String, Object> path,
            Map<String, Object> query,
            Map<String, Object> headers,
            Object body,
            BasePayloadMode base,
            List<String> omit,
            String variant,
            Map<String, String> variantMatch,
            List<FieldCorrelationRule> correlate,
            Map<String, String> save,
            FunctionalFlowExpectation expect,
            StepFuzzConfig fuzz,
            long delayMs,
            int repeat,
            PollConfig poll,
            List<Map<String, Object>> forEach,
            List<String> tags,
            String headersRef) {

        public FunctionalFlowStep {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }

        public FunctionalFlowStep(
                String name,
                String description,
                HttpMethod method,
                boolean allMethods,
                String rawTarget,
                List<String> targetPaths,
                Map<String, Object> path,
                Map<String, Object> query,
                Map<String, Object> headers,
                Object body,
                BasePayloadMode base,
                List<String> omit,
                String variant,
                Map<String, String> variantMatch,
                List<FieldCorrelationRule> correlate,
                Map<String, String> save,
                FunctionalFlowExpectation expect,
                StepFuzzConfig fuzz,
                long delayMs,
                int repeat,
                PollConfig poll,
                List<Map<String, Object>> forEach,
                List<String> tags) {
            this(name, description, method, allMethods, rawTarget, targetPaths,
                    path, query, headers, body, base, omit, variant, variantMatch,
                    correlate, save, expect, fuzz, delayMs, repeat, poll, forEach, tags, null);
        }

        public FunctionalFlowStep(
                String name,
                String description,
                HttpMethod method,
                boolean allMethods,
                String rawTarget,
                List<String> targetPaths,
                Map<String, Object> path,
                Map<String, Object> query,
                Map<String, Object> headers,
                Object body,
                BasePayloadMode base,
                List<String> omit,
                String variant,
                Map<String, String> variantMatch,
                List<FieldCorrelationRule> correlate,
                Map<String, String> save,
                FunctionalFlowExpectation expect,
                StepFuzzConfig fuzz,
                long delayMs,
                int repeat,
                PollConfig poll,
                List<Map<String, Object>> forEach) {
            this(name, description, method, allMethods, rawTarget, targetPaths,
                    path, query, headers, body, base, omit, variant, variantMatch,
                    correlate, save, expect, fuzz, delayMs, repeat, poll, forEach, List.of(), null);
        }

        public FunctionalFlowStep(
                String name,
                String description,
                HttpMethod method,
                boolean allMethods,
                String rawTarget,
                List<String> targetPaths,
                Map<String, Object> path,
                Map<String, Object> query,
                Map<String, Object> headers,
                Object body,
                BasePayloadMode base,
                List<String> omit,
                String variant,
                Map<String, String> variantMatch,
                List<FieldCorrelationRule> correlate,
                Map<String, String> save,
                FunctionalFlowExpectation expect,
                StepFuzzConfig fuzz) {
            this(name, description, method, allMethods, rawTarget, targetPaths,
                    path, query, headers, body, base, omit, variant, variantMatch,
                    correlate, save, expect, fuzz, 0L, 1, null, List.of(), List.of(), null);
        }

        public boolean matchesTags(List<String> includeTags, List<String> skipTags) {
            Set<String> normalizedStepTags = normalizeTags(tags);
            Set<String> normalizedInclude = normalizeTags(includeTags);
            Set<String> normalizedSkip = normalizeTags(skipTags);
            if (!normalizedInclude.isEmpty() && normalizedStepTags.stream().noneMatch(normalizedInclude::contains)) {
                return false;
            }
            return normalizedSkip.isEmpty() || normalizedStepTags.stream().noneMatch(normalizedSkip::contains);
        }

        private static Set<String> normalizeTags(List<String> rawTags) {
            if (rawTags == null || rawTags.isEmpty()) {
                return Set.of();
            }
            return rawTags.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        }

        public String scenarioText() {
            if (StringUtils.isNotBlank(description)) {
                return description;
            }
            return name;
        }
    }
}
