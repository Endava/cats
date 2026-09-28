package com.endava.cats.command;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Conservatively orders root collection POST producers before operations that reference their IDs in request bodies.
 * Unrelated operations retain the caller's original order; cycles fall back to that deterministic order.
 */
final class OperationDependencyOrder {
    private OperationDependencyOrder() {
    }

    /** Returns paths in producer-first order without changing the supplied entries. */
    static List<Map.Entry<String, PathItem>> order(OpenAPI api, List<Map.Entry<String, PathItem>> defaultOrder) {
        Map<String, Map.Entry<String, PathItem>> entries = new LinkedHashMap<>();
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < defaultOrder.size(); i++) {
            entries.put(defaultOrder.get(i).getKey(), defaultOrder.get(i));
            rank.put(defaultOrder.get(i).getKey(), i);
        }
        Map<String, Set<String>> producersByIdentifier = new HashMap<>();
        defaultOrder.stream().filter(OperationDependencyOrder::isRootCollectionProducer)
                .forEach(entry -> producersByIdentifier.computeIfAbsent(identifier(entry.getKey()), _ -> new LinkedHashSet<>())
                        .add(entry.getKey()));
        Map<String, Set<String>> dependencies = new HashMap<>();
        defaultOrder.forEach(entry -> {
            Set<String> required = new LinkedHashSet<>();
            entry.getValue().readOperations().stream()
                    .flatMap(operation -> requestIdentifiers(api, operation).stream())
                    .map(producersByIdentifier::get).filter(Objects::nonNull)
                    .forEach(required::addAll);
            required.remove(entry.getKey());
            dependencies.put(entry.getKey(), required);
        });
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        List<Map.Entry<String, PathItem>> ordered = new ArrayList<>();
        for (Map.Entry<String, PathItem> entry : defaultOrder) {
            appendProducerFirst(entry.getKey(), entries, dependencies, rank, visited, visiting, ordered);
        }
        return ordered;
    }

    private static boolean isRootCollectionProducer(Map.Entry<String, PathItem> entry) {
        return entry.getValue().getPost() != null && !entry.getKey().contains("{") && !entry.getKey().endsWith("/");
    }

    /** Traverses inferred dependencies first, but does not recurse into cycles. */
    private static void appendProducerFirst(String path, Map<String, Map.Entry<String, PathItem>> entries,
                                            Map<String, Set<String>> dependencies, Map<String, Integer> rank,
                                            Set<String> visited, Set<String> visiting,
                                            List<Map.Entry<String, PathItem>> ordered) {
        if (visited.contains(path) || !visiting.add(path)) {
            return;
        }
        dependencies.getOrDefault(path, Set.of()).stream()
                .filter(entries::containsKey)
                .sorted(Comparator.comparingInt(rank::get))
                .forEach(dependency -> appendProducerFirst(dependency, entries, dependencies, rank, visited, visiting, ordered));
        visiting.remove(path);
        visited.add(path);
        ordered.add(entries.get(path));
    }

    /** Extracts only top-level identifier field names; nested or ambiguous relationships are not inferred. */
    private static Set<String> requestIdentifiers(OpenAPI api, Operation operation) {
        if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (MediaType media : operation.getRequestBody().getContent().values()) {
            Schema<?> schema = resolvedRequestSchema(api, media.getSchema());
            if (schema == null || schema.getProperties() == null) {
                continue;
            }
            schema.getProperties().keySet().stream()
                    .map(name -> name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT))
                    .filter(name -> name.endsWith("id") && name.length() > 2)
                    .forEach(names::add);
        }
        return names;
    }

    private static Schema<?> resolvedRequestSchema(OpenAPI api, Schema<?> schema) {
        if (schema == null || schema.get$ref() == null) {
            return schema;
        }
        if (api.getComponents() == null || api.getComponents().getSchemas() == null) {
            return null;
        }
        String ref = schema.get$ref();
        return api.getComponents().getSchemas().get(ref.substring(ref.lastIndexOf('/') + 1));
    }

    private static String identifier(String path) {
        String resource = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (resource.endsWith("ies")) {
            resource = resource.substring(0, resource.length() - 3) + "y";
        } else if (resource.endsWith("s") && !resource.endsWith("ss")) {
            resource = resource.substring(0, resource.length() - 1);
        }
        return resource + "id";
    }
}
