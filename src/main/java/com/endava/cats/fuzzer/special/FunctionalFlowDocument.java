package com.endava.cats.fuzzer.special;

import com.endava.cats.http.HttpMethod;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable model representing a functional flow YAML document with global variables,
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

    public record FieldCorrelationRule(
            List<String> paths,
            String when,
            Map<String, Object> set,
            List<String> omit) {
    }

    public record FunctionalFlowExpectation(
            List<String> status,
            Map<String, Object> fields,
            Map<String, String> headers,
            List<String> bodyContains,
            List<String> bodyNotContains,
            List<String> where,
            boolean matchesSchema) {
    }

    public record FunctionalFlowStep(
            String name,
            HttpMethod method,
            String rawTarget,
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
            FunctionalFlowExpectation expect) {
    }
}
