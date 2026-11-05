package com.endava.cats.command.triage;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.OpenApiUtils;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves request property schemas (request body properties, or path/query parameters for non-body methods)
 * from an optional OpenAPI contract so triage can validate field names and produce typed placeholder values.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class RequestSchemaLookup {
    private static final int MAX_SEARCH_DEPTH = 4;
    private static final String WILDCARD = "[*]";

    private final OpenAPI openAPI;
    private final Map<String, Schema> components;

    RequestSchemaLookup(OpenAPI openAPI) {
        this.openAPI = openAPI;
        this.components = Optional.ofNullable(openAPI)
                .map(OpenAPI::getComponents)
                .map(Components::getSchemas)
                .orElse(Map.of());
    }

    static RequestSchemaLookup none() {
        return new RequestSchemaLookup(null);
    }

    /**
     * Returns the root request schema for the operation, or empty when no contract was supplied or the operation is unknown.
     */
    Optional<Schema<?>> requestSchema(String path, String method) {
        return operation(path, method).flatMap(op -> bodySchema(op).or(() -> parametersSchema(path, op)));
    }

    /**
     * Returns the schema of the dotted {@code fieldPath} (supporting {@code [*]} array segments).
     */
    Optional<Schema<?>> property(String path, String method, String fieldPath) {
        Optional<Schema<?>> current = requestSchema(path, method);
        for (String segment : fieldPath.split("\\.", -1)) {
            current = current.flatMap(schema -> child(schema, segment));
        }
        return current;
    }

    /**
     * Finds the dotted path of the first property (breadth-first) whose alphanumeric-normalized name matches.
     */
    Optional<String> findPropertyPath(String path, String method, String normalizedName) {
        return requestSchema(path, method).flatMap(root -> breadthFirstSearch(root, normalizedName));
    }

    private Optional<Operation> operation(String path, String method) {
        if (openAPI == null || openAPI.getPaths() == null || path == null) {
            return Optional.empty();
        }
        PathItem pathItem = openAPI.getPaths().get(path);
        return Optional.ofNullable(pathItem)
                .flatMap(item -> HttpMethod.fromString(method).map(hm -> HttpMethod.getOperation(hm, item)));
    }

    private Optional<Schema<?>> bodySchema(Operation operation) {
        return Optional.ofNullable(OpenApiUtils.resolveRequestBody(operation.getRequestBody(), openAPI))
                .map(RequestBody::getContent)
                .flatMap(RequestSchemaLookup::firstMediaType)
                .map(MediaType::getSchema)
                .map(this::resolve);
    }

    private static Optional<MediaType> firstMediaType(Content content) {
        MediaType json = OpenApiUtils.getMediaTypeFromContent(content, "application/json");
        return Optional.ofNullable(json).or(() -> content.values().stream().findFirst());
    }

    /**
     * Models path/query parameters as an object schema; empty when the operation declares none.
     */
    private Optional<Schema<?>> parametersSchema(String path, Operation operation) {
        Map<String, Schema> properties = new LinkedHashMap<>();
        OpenApiUtils.collectPathAndOperationParameters(openAPI.getPaths().get(path), operation, openAPI).stream()
                .filter(parameter -> parameter.getName() != null && parameter.getSchema() != null)
                .forEach(parameter -> properties.put(parameter.getName(), resolve(parameter.getSchema())));
        return properties.isEmpty() ? Optional.empty() : Optional.of(new Schema<>().properties(properties));
    }

    private Optional<Schema<?>> child(Schema<?> parent, String segment) {
        boolean wildcard = segment.endsWith(WILDCARD);
        String name = wildcard ? segment.substring(0, segment.length() - WILDCARD.length()) : segment;
        Optional<Schema<?>> property = properties(itemsIfArray(parent)).entrySet().stream()
                .filter(entry -> entry.getKey().equals(name))
                .map(Map.Entry::getValue)
                .findFirst();
        return wildcard ? property.map(this::itemsIfArray) : property;
    }

    private Schema<?> itemsIfArray(Schema<?> schema) {
        Schema<?> resolved = resolve(schema);
        return CatsModelUtils.isArraySchema(resolved) && resolved.getItems() != null ? resolve(resolved.getItems()) : resolved;
    }

    /**
     * Collects own and composed ({@code allOf}/{@code oneOf}/{@code anyOf}) properties, resolving {@code $ref}s.
     */
    Map<String, Schema<?>> properties(Schema<?> schema) {
        Map<String, Schema<?>> merged = new LinkedHashMap<>();
        Schema<?> resolved = resolve(schema);
        Optional.ofNullable(resolved.getProperties()).orElse(Map.of())
                .forEach((name, property) -> merged.putIfAbsent(name, resolve(property)));
        CatsModelUtils.getComposedSchemas(resolved).stream()
                .map(this::resolve)
                .filter(Objects::nonNull)
                .forEach(composed -> properties(composed).forEach(merged::putIfAbsent));
        return merged;
    }

    Schema<?> resolve(Schema<?> schema) {
        return schema == null ? new Schema<>() : CatsModelUtils.resolveSchema(schema, components);
    }

    private Optional<String> breadthFirstSearch(Schema<?> root, String normalizedName) {
        Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<SearchNode> queue = new ArrayDeque<>(List.of(new SearchNode("", root, 0)));
        while (!queue.isEmpty()) {
            SearchNode node = queue.removeFirst();
            Optional<String> found = expand(node, normalizedName, visited, queue);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<String> expand(SearchNode node, String normalizedName, Set<Schema<?>> visited, Deque<SearchNode> queue) {
        Schema<?> objectSchema = itemsIfArray(node.schema());
        if (node.depth() > MAX_SEARCH_DEPTH || !visited.add(objectSchema)) {
            return Optional.empty();
        }
        for (Map.Entry<String, Schema<?>> entry : properties(objectSchema).entrySet()) {
            String childPath = node.prefix().isEmpty() ? entry.getKey() : node.prefix() + "." + entry.getKey();
            if (FieldNames.normalize(entry.getKey()).equals(normalizedName)) {
                return Optional.of(childPath);
            }
            queue.addLast(new SearchNode(arrayAwarePath(childPath, entry.getValue()), entry.getValue(), node.depth() + 1));
        }
        return Optional.empty();
    }

    private String arrayAwarePath(String childPath, Schema<?> schema) {
        return CatsModelUtils.isArraySchema(resolve(schema)) ? childPath + WILDCARD : childPath;
    }

    private record SearchNode(String prefix, Schema<?> schema, int depth) {
    }
}
