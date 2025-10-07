package com.endava.cats.util;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Computes different metrics from the OpenAPI Spec.
 */
public class OpenApiReusabilityMetrics {
    private static final Map<OpenAPI, OpenApiAnalysisCache> CACHE = new ConcurrentHashMap<>();

    // Pre-compiled schema property set for performance
    private static final Set<String> SCHEMA_PROPERTIES = Set.of(
            "type", "format", "properties", "items", "required", "enum",
            "allOf", "oneOf", "anyOf", "not", "additionalProperties",
            "minimum", "maximum", "minLength", "maxLength", "pattern",
            "minItems", "maxItems", "uniqueItems", "multipleOf"
    );

    /**
     * Cache to store expensive computations per OpenAPI instance
     */
    private static class OpenApiAnalysisCache {
        final List<Operation> operations;
        final Set<String> allComponentNames;
        final Set<String> usedComponents;
        final int totalRefs;
        final int inlineSchemas;
        final Map<String, Object> apiAsMap;

        OpenApiAnalysisCache(OpenAPI api) {
            this.operations = computeAllOperations(api);
            this.allComponentNames = computeAllComponentNames(api.getComponents());
            this.apiAsMap = convertToMap(api);
            this.usedComponents = new HashSet<>();
            collectRefTargets(apiAsMap, usedComponents);
            this.totalRefs = countRefsInObject(apiAsMap);
            this.inlineSchemas = countInlineSchemas(apiAsMap);
        }

        private static List<Operation> computeAllOperations(OpenAPI api) {
            if (api.getPaths() == null) return Collections.emptyList();

            return api.getPaths().values().stream()
                    .flatMap(path -> path.readOperations().stream())
                    .toList();
        }

        private static Set<String> computeAllComponentNames(Components c) {
            if (c == null) return Collections.emptySet();

            Set<String> allComponents = new HashSet<>();
            if (c.getSchemas() != null) {
                allComponents.addAll(c.getSchemas().keySet());
            }
            if (c.getResponses() != null) {
                allComponents.addAll(c.getResponses().keySet());
            }
            if (c.getParameters() != null) {
                allComponents.addAll(c.getParameters().keySet());
            }
            if (c.getRequestBodies() != null) {
                allComponents.addAll(c.getRequestBodies().keySet());
            }
            if (c.getHeaders() != null) {
                allComponents.addAll(c.getHeaders().keySet());
            }
            return allComponents;
        }
    }

    /**
     * Get or create cached analysis for the given OpenAPI spec
     */
    private static OpenApiAnalysisCache getOrCreateCache(OpenAPI api) {
        return CACHE.computeIfAbsent(api, OpenApiAnalysisCache::new);
    }

    /**
     * Component Invocation Coverage: percentage of operations referencing ≥1 component via $ref.
     * Good: more than 75%. Warn: 50% - 75%. Bad: less than 50%.
     */
    public static double computeComponentInvocationCoverage(OpenAPI api) {
        OpenApiAnalysisCache cache = getOrCreateCache(api);
        if (cache.operations.isEmpty()) {
            return 0.0;
        }

        long invoked = cache.operations.stream()
                .parallel() // Use parallel processing for large APIs
                .filter(op -> countRefsInObject(convertToMap(op)) > 0)
                .count();

        return (double) invoked / cache.operations.size();
    }

    /**
     * Dead‐Component Ratio: (defined components never referenced) ÷ (total components).
     * Bad: more than 10%. Warn 0% - 10%. Good: 0%
     */
    public static double computeDeadComponentRatio(OpenAPI api) {
        OpenApiAnalysisCache cache = getOrCreateCache(api);
        if (cache.allComponentNames.isEmpty()) return 0.0;

        long dead = cache.allComponentNames.stream()
                .filter(name -> !cache.usedComponents.contains(name))
                .count();

        return (double) dead / cache.allComponentNames.size();
    }

    /**
     * Inline‐Definition Ratio: inline schemas ÷ (inline + referenced definitions).
     * Bad: more than 30%. Warn: 10% - 30%. Good: less than 10%.
     */
    public static double computeInlineDefinitionRatio(OpenAPI api) {
        OpenApiAnalysisCache cache = getOrCreateCache(api);
        int totalDefs = cache.inlineSchemas + cache.totalRefs;
        return totalDefs == 0 ? 0.0 : (double) cache.inlineSchemas / totalDefs;
    }

    /**
     * Average References per Operation.
     * Good: > 2. Warn: 1 - 2. Bad: < 1.
     */
    public static double computeAverageRefsPerOperation(OpenAPI api) {
        OpenApiAnalysisCache cache = getOrCreateCache(api);
        if (cache.operations.isEmpty()) {
            return 0.0;
        }

        int totalRefs = cache.operations.stream()
                .parallel()
                .mapToInt(op -> countRefsInObject(convertToMap(op)))
                .sum();

        return (double) totalRefs / cache.operations.size();
    }

    /**
     * Unique‐Target Reuse Ratio: (# distinct $ref targets) ÷ (total components).
     * Good: more than 50%. Warn: 20% - 50%. Bad: less than 20%.
     */
    public static double computeUniqueTargetReuseRatio(OpenAPI api) {
        OpenApiAnalysisCache cache = getOrCreateCache(api);
        if (cache.allComponentNames.isEmpty()) {
            return 0.0;
        }

        return (double) cache.usedComponents.size() / cache.allComponentNames.size();
    }

    /**
     * Error Response Name Diversity Ratio.
     * Good: less than 10%; Warn 10% - 30%; Bad more than 30%
     */
    public static double computeErrorResponseNameDiversity(OpenAPI api) {
        OpenApiAnalysisCache cache = getOrCreateCache(api);

        ErrorResponseAnalysis analysis = cache.operations.stream()
                .parallel()
                .map(OpenApiReusabilityMetrics::analyzeErrorResponses)
                .reduce(new ErrorResponseAnalysis(), ErrorResponseAnalysis::merge);

        return analysis.totalErrorResponses == 0
                ? 0.0
                : (double) analysis.distinctNames.size() / analysis.totalErrorResponses;
    }

    /**
     * Helper class for error response analysis
     */
    private static class ErrorResponseAnalysis {
        int totalErrorResponses = 0;
        Set<String> distinctNames = new HashSet<>();

        ErrorResponseAnalysis merge(ErrorResponseAnalysis other) {
            ErrorResponseAnalysis merged = new ErrorResponseAnalysis();
            merged.totalErrorResponses = this.totalErrorResponses + other.totalErrorResponses;
            merged.distinctNames = new HashSet<>(this.distinctNames);
            merged.distinctNames.addAll(other.distinctNames);
            return merged;
        }
    }

    /**
     * Analyze error responses for a single operation
     */
    private static ErrorResponseAnalysis analyzeErrorResponses(Operation op) {
        ErrorResponseAnalysis analysis = new ErrorResponseAnalysis();

        List<ApiResponse> errorResponses = op.getResponses().entrySet().stream()
                .filter(e -> e.getKey().matches("^[45]\\d{2}$"))
                .map(Map.Entry::getValue)
                .toList();

        for (ApiResponse resp : errorResponses) {
            analysis.totalErrorResponses++;
            String name = extractErrorResponseName(resp);
            analysis.distinctNames.add(name);
        }

        return analysis;
    }

    /**
     * Extract error response name from ApiResponse
     */
    private static String extractErrorResponseName(ApiResponse resp) {
        // Direct $ref
        if (resp.get$ref() != null) {
            return resp.get$ref().substring(resp.get$ref().lastIndexOf('/') + 1);
        }

        // Inline but schema $ref
        if (resp.getContent() != null) {
            return resp.getContent().values().stream()
                    .map(MediaType::getSchema)
                    .filter(s -> s != null && s.get$ref() != null)
                    .map(s -> s.get$ref().substring(s.get$ref().lastIndexOf('/') + 1))
                    .findFirst()
                    .orElse("<inline-or-unknown>");
        }

        return "<inline-or-unknown>";
    }

    /**
     * Tag Cohesion Index: (total tag assignments) ÷ (number of distinct tags)
     * Good: more than 5, Warning: 2 - 5, Bad: less than 2.
     */
    public static double computeTagCohesionIndex(OpenAPI api) {
        OpenApiAnalysisCache cache = getOrCreateCache(api);
        if (cache.operations.isEmpty()) {
            return 0.0;
        }

        Map<String, Long> tagCounts = cache.operations.stream()
                .parallel()
                .flatMap(op -> {
                    List<String> tags = op.getTags();
                    return tags != null ? tags.stream() : Stream.empty();
                })
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        if (tagCounts.isEmpty()) {
            return 0.0;
        }

        long totalAssignments = tagCounts.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        return (double) totalAssignments / tagCounts.size();
    }

    /**
     * Optional to Required Field Ratio: (optional fields) ÷ (total fields).
     *
     * <p>
     * Good: less than 20%. Warn: 20% - 50%. Bad: more than 50%.
     */
    public static double computeOptionalToRequiredFieldRatio(OpenAPI api) {
        OpenApiAnalysisCache cache = getOrCreateCache(api);

        List<Map<?, ?>> schemas = new ArrayList<>();
        collectSchemaMaps(cache.apiAsMap, schemas);

        // 2. Count total and optional properties
        int totalProps = 0;
        int optionalProps = 0;
        for (Map<?, ?> schema : schemas) {
            Object propsObj = schema.get("properties");
            if (!(propsObj instanceof Map<?, ?> props)) {
                continue;
            }
            int propCount = props.size();
            totalProps += propCount;

            Object reqObj = schema.get("required");
            int requiredCount = 0;
            if (reqObj instanceof List) {
                requiredCount = ((List<?>) reqObj).size();
            }
            optionalProps += (propCount - requiredCount);
        }

        // 3. Compute ratio
        return totalProps == 0
                ? 0.0
                : (double) optionalProps / totalProps;
    }

    /**
     * Compute all metrics at once for better performance
     */
    public static OpenApiMetricsResult computeAllMetrics(OpenAPI api) {
        return new OpenApiMetricsResult(
                computeComponentInvocationCoverage(api),
                computeDeadComponentRatio(api),
                computeInlineDefinitionRatio(api),
                computeAverageRefsPerOperation(api),
                computeUniqueTargetReuseRatio(api),
                computeErrorResponseNameDiversity(api),
                computeTagCohesionIndex(api),
                computeOptionalToRequiredFieldRatio(api)
        );
    }

    /**
     * Result class for all metrics
     */
    public record OpenApiMetricsResult(double componentInvocationCoverage, double deadComponentRatio,
                                       double inlineDefinitionRatio, double averageRefsPerOperation,
                                       double uniqueTargetReuseRatio, double errorResponseNameDiversity,
                                       double tagCohesionIndex, double optionalToRequiredFieldRatio) {

        @Override
        public String toString() {
            return String.format(
                    "OpenAPI Metrics:%n" +
                            "  Component Invocation Coverage: %.2f%%%n" +
                            "  Dead Component Ratio: %.2f%%%n" +
                            "  Inline Definition Ratio: %.2f%%%n" +
                            "  Average Refs per Operation: %.2f%n" +
                            "  Unique Target Reuse Ratio: %.2f%%%n" +
                            "  Error Response Name Diversity: %.2f%%%n" +
                            "  Tag Cohesion Index: %.2f%n" +
                            "  Optional to Required Field Ratio: %.2f%%",
                    componentInvocationCoverage * 100,
                    deadComponentRatio * 100,
                    inlineDefinitionRatio * 100,
                    averageRefsPerOperation,
                    uniqueTargetReuseRatio * 100,
                    errorResponseNameDiversity * 100,
                    tagCohesionIndex,
                    optionalToRequiredFieldRatio * 100
            );
        }
    }


    // ─── Helper Methods ───────────────────────────────────────────────────────────────


    private static Map<String, Object> convertToMap(Object obj) {
        if (obj == null) {
            return Collections.emptyMap();
        }
        try {
            return JsonUtils.getSimpleObjectMapper().convertValue(obj,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static int countRefsInObject(Object obj) {
        switch (obj) {
            case null -> {
                return 0;
            }
            case Map<?, ?> map -> {
                int count = map.containsKey("$ref") ? 1 : 0;
                return count + map.values().stream()
                        .mapToInt(OpenApiReusabilityMetrics::countRefsInObject)
                        .sum();
            }
            case Collection<?> collection -> {
                return collection.stream()
                        .mapToInt(OpenApiReusabilityMetrics::countRefsInObject)
                        .sum();
            }
            default -> {
                //don't do anything on other cases
            }
        }

        return 0;
    }

    private static void collectRefTargets(Object obj, Set<String> targets) {
        switch (obj) {
            case null -> {
                return;
            }
            case Map<?, ?> map -> {
                Object ref = map.get("$ref");
                if (ref instanceof String refStr && refStr.startsWith("#/components/")) {
                    String componentName = refStr.substring(refStr.lastIndexOf('/') + 1);
                    targets.add(componentName);
                }
                map.values().forEach(value -> collectRefTargets(value, targets));
            }
            case Collection<?> collection -> collection.forEach(item -> collectRefTargets(item, targets));
            default -> {
                //don't do anything on other cases
            }
        }

    }

    private static int countInlineSchemas(Object obj) {
        switch (obj) {
            case null -> {
                return 0;
            }
            case Map<?, ?> map -> {
                boolean isSchema = hasSchemaProperties(map);
                boolean isReference = map.containsKey("$ref");

                int count = (isSchema && !isReference) ? 1 : 0;
                return count + map.values().stream()
                        .mapToInt(OpenApiReusabilityMetrics::countInlineSchemas)
                        .sum();
            }
            case Collection<?> collection -> {
                return collection.stream()
                        .mapToInt(OpenApiReusabilityMetrics::countInlineSchemas)
                        .sum();
            }
            default -> {
                //don't do anything for other cases
            }
        }

        return 0;
    }

    private static boolean hasSchemaProperties(Map<?, ?> map) {
        return map.keySet().stream()
                .anyMatch(key -> key instanceof String && SCHEMA_PROPERTIES.contains(key));
    }

    private static void collectSchemaMaps(Object obj, List<Map<?, ?>> schemas) {
        if (obj instanceof Map<?, ?> map) {
            if (map.containsKey("properties") && map.get("properties") instanceof Map) {
                schemas.add(map);
            }
            for (Object child : map.values()) {
                collectSchemaMaps(child, schemas);
            }
        } else if (obj instanceof Collection) {
            for (Object item : (Collection<?>) obj) {
                collectSchemaMaps(item, schemas);
            }
        }
    }
}