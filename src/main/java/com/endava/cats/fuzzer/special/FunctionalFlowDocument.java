package com.endava.cats.fuzzer.special;

import com.endava.cats.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable model representing a functional or security flow YAML document with global variables,
 * cross-field correlation rules, and sequential HTTP steps.
 */
public record FunctionalFlowDocument(
        Map<String, String> variables,
        List<FieldCorrelationRule> correlate,
        List<FunctionalFlowStep> steps) {

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
            StepFuzzConfig fuzz) {

        public String scenarioText() {
            if (StringUtils.isNotBlank(description)) {
                return description;
            }
            return name;
        }
    }
}
