package com.endava.cats.command;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.util.WordUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservatively orders root collection POST producers before operations that reference their IDs in request bodies,
 * path parameters, or query parameters.
 * Unrelated operations retain the caller's original order; cycles fall back to that deterministic order.
 */
final class OperationDependencyOrder {
    private static final int MAX_SCHEMA_DEPTH = 4;
    private static final Pattern PATH_PARAMETER_PATTERN = Pattern.compile("\\{([^/]+)}");
    private static final List<String> ID_SUFFIXES = List.of("id", "uuid", "guid", "token");

    private OperationDependencyOrder() {
    }

    /**
     * Returns paths in producer-first order without changing the supplied entries.
     */
    static List<Map.Entry<String, PathItem>> order(OpenAPI api, List<Map.Entry<String, PathItem>> defaultOrder) {
        Map<String, Map.Entry<String, PathItem>> entries = new LinkedHashMap<>();
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < defaultOrder.size(); i++) {
            entries.put(defaultOrder.get(i).getKey(), defaultOrder.get(i));
            rank.put(defaultOrder.get(i).getKey(), i);
        }
        Map<String, Set<String>> producersByIdentifier = buildProducersByIdentifier(api, defaultOrder);
        Map<String, Set<String>> dependencies = buildDependencies(api, defaultOrder, producersByIdentifier);
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        List<Map.Entry<String, PathItem>> ordered = new ArrayList<>();
        for (Map.Entry<String, PathItem> entry : defaultOrder) {
            appendProducerFirst(entry.getKey(), entries, dependencies, rank, visited, visiting, ordered);
        }
        return ordered;
    }

    private static Map<String, Set<String>> buildProducersByIdentifier(OpenAPI api, List<Map.Entry<String, PathItem>> defaultOrder) {
        Map<String, Set<String>> producersByIdentifier = new HashMap<>();
        defaultOrder.stream().filter(OperationDependencyOrder::isRootCollectionProducer)
                .forEach(entry -> {
                    producerIdentifiers(entry.getKey()).forEach(id ->
                            producersByIdentifier.computeIfAbsent(id, _ -> new LinkedHashSet<>()).add(entry.getKey()));
                    if (entry.getValue().getPost() != null) {
                        responseIdentifiers(api, entry.getValue().getPost()).forEach(id ->
                                producersByIdentifier.computeIfAbsent(id, _ -> new LinkedHashSet<>()).add(entry.getKey()));
                    }
                });
        return producersByIdentifier;
    }

    private static Map<String, Set<String>> buildDependencies(OpenAPI api, List<Map.Entry<String, PathItem>> defaultOrder,
                                                              Map<String, Set<String>> producersByIdentifier) {
        Map<String, Set<String>> postPropsByPath = new HashMap<>();
        defaultOrder.stream()
                .filter(entry -> entry.getValue().getPost() != null && !stripTrailingSlash(entry.getKey()).contains("{"))
                .forEach(entry -> postPropsByPath.put(entry.getKey(), postRequestPropertyNames(api, entry.getValue().getPost())));
        Map<String, Set<String>> dependencies = new HashMap<>();
        for (Map.Entry<String, PathItem> entry : defaultOrder) {
            Set<String> required = new LinkedHashSet<>();
            operationIdentifiers(api, entry.getKey(), entry.getValue()).stream()
                    .map(producersByIdentifier::get).filter(Objects::nonNull)
                    .forEach(required::addAll);
            addSiblingSupersetProducers(entry.getKey(), postPropsByPath, required);
            required.remove(entry.getKey());
            dependencies.put(entry.getKey(), required);
        }
        return dependencies;
    }

    private static void addSiblingSupersetProducers(String path, Map<String, Set<String>> postPropsByPath, Set<String> required) {
        Set<String> myProps = postPropsByPath.getOrDefault(path, Set.of());
        String parentDir = parentDirectory(path);
        if (myProps.isEmpty() || parentDir.isEmpty()) {
            return;
        }
        postPropsByPath.forEach((otherPath, otherProps) -> {
            if (!otherPath.equals(path) && parentDir.equals(parentDirectory(otherPath)) &&
                    otherProps.size() > myProps.size() && otherProps.containsAll(myProps)) {
                required.add(otherPath);
            }
        });
    }

    private static boolean isRootCollectionProducer(Map.Entry<String, PathItem> entry) {
        String trimmed = stripTrailingSlash(entry.getKey());
        return (entry.getValue().getPost() != null || entry.getValue().getGet() != null) && !trimmed.contains("{");
    }

    private static String stripTrailingSlash(String path) {
        if (path == null || path.length() <= 1) {
            return path == null ? "" : path;
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * Traverses inferred dependencies first, but does not recurse into cycles.
     */
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

    private static Set<String> operationIdentifiers(OpenAPI api, String path, PathItem pathItem) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PATH_PARAMETER_PATTERN.matcher(path);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String precedingSegment = precedingSegment(path, matcher.start());
            addIdentifierCandidate(paramName, precedingSegment, names);
        }
        Optional.ofNullable(pathItem.getParameters()).orElse(List.of())
                .forEach(parameter -> addParameterIdentifier(api, parameter, names));
        for (Operation operation : pathItem.readOperations()) {
            Optional.ofNullable(operation.getParameters()).orElse(List.of())
                    .forEach(parameter -> addParameterIdentifier(api, parameter, names));
            names.addAll(requestIdentifiers(api, operation));
        }
        return names;
    }

    private static String precedingSegment(String path, int paramStartIndex) {
        String prefix = stripTrailingSlash(path.substring(0, paramStartIndex));
        int slash = prefix.lastIndexOf('/');
        return slash >= 0 ? prefix.substring(slash + 1) : prefix;
    }

    private static void addParameterIdentifier(OpenAPI api, Parameter parameter, Set<String> names) {
        Parameter resolved = resolvedParameter(api, parameter);
        if (resolved != null && resolved.getName() != null) {
            addIdentifierCandidate(resolved.getName(), "", names);
        }
    }

    private static Parameter resolvedParameter(OpenAPI api, Parameter parameter) {
        if (parameter == null || parameter.get$ref() == null) {
            return parameter;
        }
        if (api.getComponents() == null || api.getComponents().getParameters() == null) {
            return null;
        }
        String ref = parameter.get$ref();
        return api.getComponents().getParameters().get(ref.substring(ref.lastIndexOf('/') + 1));
    }

    private static Set<String> requestIdentifiers(OpenAPI api, Operation operation) {
        if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (MediaType media : operation.getRequestBody().getContent().values()) {
            Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            collectSchemaIdentifiers(api, media.getSchema(), "", 0, visited, names);
        }
        return names;
    }

    private static Set<String> responseIdentifiers(OpenAPI api, Operation operation) {
        if (operation == null || operation.getResponses() == null) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        operation.getResponses().entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().startsWith("2") && entry.getValue() != null && entry.getValue().getContent() != null)
                .flatMap(entry -> entry.getValue().getContent().values().stream())
                .forEach(media -> {
                    Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                    collectSchemaIdentifiers(api, media.getSchema(), "", 0, visited, names);
                });
        return names;
    }

    private static Set<String> postRequestPropertyNames(OpenAPI api, Operation operation) {
        if (operation == null || operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return Set.of();
        }
        Set<String> props = new LinkedHashSet<>();
        for (MediaType media : operation.getRequestBody().getContent().values()) {
            Schema<?> resolved = resolvedRequestSchema(api, media.getSchema());
            if (resolved != null && resolved.getProperties() != null) {
                resolved.getProperties().keySet().forEach(key -> props.add(normalize(key)));
            }
        }
        return props;
    }

    private static String parentDirectory(String path) {
        String trimmed = stripTrailingSlash(path);
        int slash = trimmed.lastIndexOf('/');
        return slash > 0 ? trimmed.substring(0, slash) : "";
    }

    private static void collectSchemaIdentifiers(OpenAPI api, Schema<?> schema, String parentProperty, int depth,
                                                 Set<Schema<?>> visited, Set<String> names) {
        Schema<?> resolved = resolvedRequestSchema(api, schema);
        if (resolved == null || depth > MAX_SCHEMA_DEPTH || !visited.add(resolved)) {
            return;
        }
        try {
            if (resolved.getProperties() != null) {
                for (Map.Entry<String, Schema> entry : resolved.getProperties().entrySet()) {
                    String propertyName = entry.getKey();
                    addIdentifierCandidate(propertyName, parentProperty, names);
                    collectSchemaIdentifiers(api, entry.getValue(), propertyName, depth + 1, visited, names);
                }
            }
            for (Schema<?> subSchema : com.endava.cats.util.CatsModelUtils.getComposedSchemas(resolved)) {
                collectSchemaIdentifiers(api, subSchema, parentProperty, depth + 1, visited, names);
            }
            if (resolved.getItems() != null) {
                collectSchemaIdentifiers(api, resolved.getItems(), parentProperty, depth + 1, visited, names);
            }
        } finally {
            visited.remove(resolved);
        }
    }

    private static void addIdentifierCandidate(String rawName, String parentProperty, Set<String> names) {
        String normalized = normalize(rawName);
        if (normalized.isEmpty()) {
            return;
        }
        names.add("noun:" + normalized);
        if (ID_SUFFIXES.contains(normalized)) {
            String normalizedParent = normalize(parentProperty);
            if (!normalizedParent.isEmpty()) {
                WordUtils.nounForms(normalizedParent).forEach(form -> names.add(form + normalized));
            }
            return;
        }
        for (String suffix : ID_SUFFIXES) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                names.add(normalized);
                return;
            }
            String pluralSuffix = suffix + "s";
            if (normalized.endsWith(pluralSuffix) && normalized.length() > pluralSuffix.length()) {
                String prefix = normalized.substring(0, normalized.length() - pluralSuffix.length());
                WordUtils.nounForms(prefix).forEach(form -> names.add(form + suffix));
                return;
            }
        }
    }

    private static Schema<?> resolvedRequestSchema(OpenAPI api, Schema<?> schema) {
        if (schema == null || schema.get$ref() == null) {
            return schema;
        }
        Map<String, Schema> schemas = (api != null && api.getComponents() != null)
                ? api.getComponents().getSchemas() : null;
        Schema<?> resolved = com.endava.cats.util.CatsModelUtils.resolveSchema(schema, schemas);
        return resolved == schema ? null : resolved;
    }

    private static Set<String> producerIdentifiers(String path) {
        String trimmed = stripTrailingSlash(path);
        String resource = normalize(trimmed.substring(trimmed.lastIndexOf('/') + 1));
        Set<String> identifiers = new LinkedHashSet<>();
        for (String form : WordUtils.nounForms(resource)) {
            identifiers.add("noun:" + form);
            for (String suffix : ID_SUFFIXES) {
                identifiers.add(form + suffix);
            }
        }
        return identifiers;
    }

    private static String normalize(String value) {
        return WordUtils.normalizeAlphaNumeric(value);
    }

    /**
     * Authoritative static analysis of path-parameter producers and consumers across an OpenAPI contract,
     * shared by {@link AnalyzeCommand} and {@link InitCommand}.
     */
    static PathDependencyAnalysis analyzePathDependencies(OpenAPI api) {
        if (api == null || api.getPaths() == null || api.getPaths().isEmpty()) {
            return new PathDependencyAnalysis(List.of(), Map.of(), Map.of(), Map.of(), 0, 0);
        }
        List<Map.Entry<String, PathItem>> orderedEntries = order(api, new ArrayList<>(api.getPaths().entrySet()));
        List<String> orderedPaths = orderedEntries.stream().map(Map.Entry::getKey).toList();
        Map<String, String> postProducersByNoun = collectProducersByNoun(api, true);
        Map<String, String> allProducersByNoun = collectProducersByNoun(api, false);
        Map<String, Set<String>> postProducersById = buildProducersByIdentifier(api, orderedEntries);

        Map<String, Set<String>> consumersByProducer = new LinkedHashMap<>();
        Map<String, Set<String>> paramsByProducer = new LinkedHashMap<>();
        Map<String, Set<String>> unresolvedByPath = new LinkedHashMap<>();
        int totalPathVars = 0;
        int resolvedPathVars = 0;

        for (String path : orderedPaths) {
            Matcher matcher = PATH_PARAMETER_PATTERN.matcher(path);
            String methods = methodsForPath(api, path);
            while (matcher.find()) {
                totalPathVars++;
                String varName = matcher.group(1);
                String preceding = normalize(precedingSegment(path, matcher.start()));
                String targetNoun = preceding.isEmpty() ? inferNounFromVarName(varName) : preceding;
                String producer = matchProducer(varName, preceding, targetNoun, allProducersByNoun, postProducersById, api);
                if (producer != null) {
                    resolvedPathVars++;
                    paramsByProducer.computeIfAbsent(producer, _ -> new LinkedHashSet<>()).add("{" + varName + "}");
                    consumersByProducer.computeIfAbsent(producer, _ -> new LinkedHashSet<>()).add(methods + " " + path);
                }
                if (matchProducer(varName, preceding, targetNoun, postProducersByNoun, postProducersById, api) == null) {
                    unresolvedByPath.computeIfAbsent(path, _ -> new LinkedHashSet<>()).add(varName);
                }
            }
        }
        return new PathDependencyAnalysis(orderedPaths, consumersByProducer, paramsByProducer,
                unresolvedByPath, totalPathVars, resolvedPathVars);
    }

    private static Map<String, String> collectProducersByNoun(OpenAPI api, boolean postOnly) {
        Map<String, String> producers = new LinkedHashMap<>();
        for (Map.Entry<String, PathItem> entry : api.getPaths().entrySet()) {
            PathItem item = entry.getValue();
            if (item == null) {
                continue;
            }
            String noun = lastConcreteSegment(entry.getKey());
            if (noun.isEmpty()) {
                continue;
            }
            if (item.getPost() != null) {
                producers.put(noun, "POST " + entry.getKey());
            } else if (!postOnly && item.getGet() != null && !stripTrailingSlash(entry.getKey()).endsWith("}")) {
                producers.putIfAbsent(noun, "GET " + entry.getKey());
            }
        }
        return producers;
    }

    private static String matchProducer(String varName, String preceding, String targetNoun,
                                        Map<String, String> producersByNoun,
                                        Map<String, Set<String>> postProducersById, OpenAPI api) {
        for (Map.Entry<String, String> entry : producersByNoun.entrySet()) {
            if (WordUtils.sameNoun(entry.getKey(), targetNoun)) {
                return entry.getValue();
            }
        }
        Set<String> candidates = new LinkedHashSet<>();
        addIdentifierCandidate(varName, preceding, candidates);
        for (String candidate : candidates) {
            Set<String> paths = postProducersById.get(candidate);
            if (paths != null && !paths.isEmpty()) {
                String prodPath = paths.iterator().next();
                PathItem item = api.getPaths().get(prodPath);
                String verb = (item != null && item.getPost() != null) ? "POST " : "GET ";
                return verb + prodPath;
            }
        }
        return null;
    }

    private static String inferNounFromVarName(String varName) {
        String stripped = varName.replaceAll("(?i)(Id|Uuid|Guid|Code|Key|Reference)$", "");
        return normalize(stripped.isEmpty() ? varName : stripped);
    }

    private static String lastConcreteSegment(String path) {
        List<String> segments = com.google.common.base.Splitter.on('/').omitEmptyStrings().splitToList(stripTrailingSlash(path));
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (!segments.get(i).startsWith("{")) {
                return normalize(segments.get(i));
            }
        }
        return "";
    }

    static String methodsForPath(OpenAPI openAPI, String path) {
        PathItem item = openAPI.getPaths() != null ? openAPI.getPaths().get(path) : null;
        if (item == null || item.readOperationsMap() == null || item.readOperationsMap().isEmpty()) {
            return HttpMethod.GET.name();
        }
        return item.readOperationsMap().keySet().stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining("/"));
    }

    record PathDependencyAnalysis(List<String> orderedPaths,
                                  Map<String, Set<String>> consumersByProducer,
                                  Map<String, Set<String>> paramsByProducer,
                                  Map<String, Set<String>> unresolvedByPath,
                                  int totalPathVars,
                                  int resolvedPathVars) {
    }
}
