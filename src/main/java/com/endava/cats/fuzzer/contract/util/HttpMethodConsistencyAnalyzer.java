package com.endava.cats.fuzzer.contract.util;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class HttpMethodConsistencyAnalyzer {
    public static final int MAX_PATH_DEPTH = 4;
    public static final Set<String> UPDATE_METHODS = Set.of("put", "patch");

    public boolean isItemPath(String path) {
        return path.matches(".*/\\{[^/]+}$");
    }

    public boolean isActionPath(String path) {
        return isActionPath(path, null);
    }

    public boolean isActionPath(String path, OpenAPI openAPI) {
        if (isItemPath(path)) {
            return false;
        }
        return PathNamingRules.isOperationOrSingletonSegment(path, openAPI);
    }

    public String extractResourceGroupKey(String path) {
        String[] segments = path.split("/", -1);
        List<String> normalized = buildNormalizedSegments(segments);

        if (shouldRemoveLastIdSegment(normalized)) {
            normalized.removeLast();
        }

        return "/" + String.join("/", normalized);
    }

    private List<String> buildNormalizedSegments(String[] segments) {
        List<String> normalized = new ArrayList<>();

        for (String segment : segments) {
            if (StringUtils.isBlank(segment)) {
                continue;
            }

            normalized.add(normalizeSegment(segment));
        }

        return normalized;
    }

    private String normalizeSegment(String segment) {
        return segment.startsWith("{") && segment.endsWith("}") ? "{}" : segment.toLowerCase(Locale.ROOT);
    }

    private boolean shouldRemoveLastIdSegment(List<String> normalized) {
        return !normalized.isEmpty() && "{}".equals(normalized.getLast()) && normalized.size() > 1;
    }

    public String deriveItemPathFromGroup(String group) {
        return group + "/{id}";
    }

    public String deriveCollectionPathFromGroup(String group) {
        return group;
    }

    public ResourcePathData collectResourceData(OpenAPI openAPI) {
        Map<String, Set<String>> collectionMethods = new HashMap<>();
        Map<String, Set<String>> itemMethods = new HashMap<>();
        Map<String, Set<String>> groupToPaths = new HashMap<>();
        Map<String, Set<String>> pathToMethods = new HashMap<>();

        if (openAPI == null || openAPI.getPaths() == null) {
            return new ResourcePathData(collectionMethods, itemMethods, groupToPaths, pathToMethods);
        }

        openAPI.getPaths().forEach((path, pathItem) -> {
            if (!isActionPath(path, openAPI)) {
                processResourcePath(path, pathItem, collectionMethods, itemMethods, groupToPaths, pathToMethods);
            }
        });

        return new ResourcePathData(collectionMethods, itemMethods, groupToPaths, pathToMethods);
    }

    private void processResourcePath(String path, PathItem pathItem,
                                     Map<String, Set<String>> collectionMethods,
                                     Map<String, Set<String>> itemMethods,
                                     Map<String, Set<String>> groupToPaths,
                                     Map<String, Set<String>> pathToMethods) {
        String groupKey = extractResourceGroupKey(path);
        Set<String> methods = extractHttpMethods(pathItem);

        groupToPaths.computeIfAbsent(groupKey, k -> new HashSet<>()).add(path);
        pathToMethods.put(path, methods);

        if (isItemPath(path)) {
            itemMethods.computeIfAbsent(groupKey, k -> new HashSet<>()).addAll(methods);
        } else {
            collectionMethods.computeIfAbsent(groupKey, k -> new HashSet<>()).addAll(methods);
        }
    }

    private Set<String> extractHttpMethods(PathItem pathItem) {
        return pathItem.readOperationsMap().keySet().stream()
                .map(Enum::name).map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    public record ResourcePathData(Map<String, Set<String>> collectionMethods,
                                   Map<String, Set<String>> itemMethods,
                                   Map<String, Set<String>> groupToPaths,
                                   Map<String, Set<String>> pathMethods) {

        public Set<String> getGroups() {
            return groupToPaths.keySet();
        }

        public Set<String> getPathsForGroup(String group) {
            return groupToPaths.getOrDefault(group, Set.of());
        }

        public Set<String> getItemMethods(String group) {
            return itemMethods.getOrDefault(group, Set.of());
        }
    }
}