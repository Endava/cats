package com.endava.cats.pool;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.util.WordUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record ResourceInstance(long sequence, String sourcePath, HttpMethod sourceMethod,
                        Map<String, StoredValue> values, boolean fuzzed, boolean active,
                        List<String> pathSegments, String resourceName, Set<String> resourceNameForms,
                        boolean collectionPath, Set<String> allAliases, Set<String> requestBodyAliases) {

    ResourceInstance(long sequence, String sourcePath, HttpMethod sourceMethod,
                     Map<String, StoredValue> values, boolean fuzzed, boolean active) {
        this(sequence, sourcePath, sourceMethod, values, fuzzed, active,
                ResourceSemanticNaming.pathSegments(sourcePath),
                ResourceSemanticNaming.resourceName(sourcePath),
                WordUtils.nounForms(ResourceSemanticNaming.resourceName(sourcePath)),
                ResourceSemanticNaming.isCollectionPath(sourcePath),
                collectAliases(values, false),
                collectAliases(values, true));
    }

    private static Set<String> collectAliases(Map<String, StoredValue> values, boolean requestBodyOnly) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (StoredValue value : values.values()) {
            if (!requestBodyOnly || value.sourceLocation().startsWith("request.body.")) {
                result.addAll(value.aliases());
            }
        }
        return Set.copyOf(result);
    }

    boolean hasAnyAlias(Set<String> candidates) {
        for (String candidate : candidates) {
            if (allAliases.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    ResourceInstance deleted() {
        return new ResourceInstance(sequence, sourcePath, sourceMethod, values, fuzzed, false,
                pathSegments, resourceName, resourceNameForms, collectionPath, allAliases, requestBodyAliases);
    }
}
