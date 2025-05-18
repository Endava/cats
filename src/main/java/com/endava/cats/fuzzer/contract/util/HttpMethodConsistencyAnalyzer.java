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

    private static final Set<String> ACTION_VERBS = Set.of(
            "activate", "deactivate", "suspend", "resume", "reset", "revoke", "approve", "reject", "decline",
            "login", "logout", "refresh", "reauthenticate", "reauthorize", "validate", "verify",
            "submit", "process", "finalize", "complete", "execute", "resend", "retry", "reprocess",
            "batch", "bulk", "multi", "mass", "import", "export", "sync", "merge",
            "search", "filter", "lookup", "find", "query",
            "report", "reports", "dashboard", "analytics", "statistics", "metrics", "summary",
            "history", "audit", "log", "logs",
            "copy", "clone", "extend", "cancel", "preview", "archive", "unarchive", "download",
            "upload", "transition", "states", "state", "status"
    );

    public boolean isItemPath(String path) {
        return path.matches(".*/\\{[^/]+}$");
    }

    public boolean isActionPath(String path) {
        String[] segments = path.split("/", -1);
        return segments.length > 0 && ACTION_VERBS.contains(segments[segments.length - 1].toLowerCase(Locale.ROOT));
    }

    public String extractResourceGroupKey(String path) {
        String[] segments = path.split("/", -1);
        List<String> normalized = new ArrayList<>();
        int depth = 0;

        for (String segment : segments) {
            if (StringUtils.isBlank(segment)) {
                continue;
            }
            normalized.add(segment.startsWith("{") && segment.endsWith("}") ? "{}" : segment.toLowerCase(Locale.ROOT));
            if (++depth >= MAX_PATH_DEPTH) {
                break;
            }
        }

        if (!normalized.isEmpty() && "{}".equals(normalized.getLast()) && normalized.size() > 1) {
            normalized.removeLast();
        }

        return "/" + String.join("/", normalized);
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

        openAPI.getPaths().forEach((path, pathItem) -> {
            if (isActionPath(path)) {
                return;
            }

            String groupKey = extractResourceGroupKey(path);
            Set<String> methods = extractHttpMethods(pathItem);

            groupToPaths.computeIfAbsent(groupKey, k -> new HashSet<>()).add(path);
            pathToMethods.put(path, methods);

            if (isItemPath(path)) {
                itemMethods.computeIfAbsent(groupKey, k -> new HashSet<>()).addAll(methods);
            } else {
                collectionMethods.computeIfAbsent(groupKey, k -> new HashSet<>()).addAll(methods);
            }
        });

        return new ResourcePathData(collectionMethods, itemMethods, groupToPaths, pathToMethods);
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
