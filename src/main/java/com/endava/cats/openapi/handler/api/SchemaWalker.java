package com.endava.cats.openapi.handler.api;

import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Deep-schema walker with pluggable handlers and path/method context propagation.
 */
@Singleton
public class SchemaWalker {

    Instance<SchemaHandler> handlers;

    public SchemaWalker(Instance<SchemaHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * Initializes the schema handlers for the given OpenAPI document.
     * <p>
     * This method retrieves all available {@link SchemaHandler} instances and invokes them
     * for each schema in the OpenAPI document, allowing custom processing of schemas.
     *
     * @param openAPI the OpenAPI document to walk
     */
    public void initHandlers(OpenAPI openAPI) {
        walk(openAPI, handlers.stream().toArray(SchemaHandler[]::new));
    }

    /**
     * Walks the OpenAPI document, invoking handlers for each schema encountered.
     * <p>
     * This method traverses the OpenAPI document, including components, paths, operations,
     * parameters, request bodies, and responses. It handles schema references and cycles
     * using depth-first search (DFS) while maintaining context (path, method).
     *
     * @param api      the OpenAPI document to walk
     * @param handlers the handlers to invoke for each schema
     */
    public static void walk(OpenAPI api, SchemaHandler... handlers) {
        Objects.requireNonNull(api, "openApi");
        Objects.requireNonNull(handlers, "handlers");

        Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        Optional.ofNullable(api.getComponents())
                .map(Components::getSchemas)
                .ifPresent(schemas ->
                        schemas.forEach((name, schema) -> {
                            Deque<String> p = new ArrayDeque<>(List.of(name));
                            dfs(schema, api, p, visited, handlers, null, null);
                        }));

        Optional.ofNullable(api.getPaths()).ifPresent(paths ->
                paths.forEach((url, item) ->
                        processPathItem(item, api, url, visited, handlers)));
    }

    private static void processPathItem(PathItem item, OpenAPI api, String url,
                                        Set<Schema<?>> visited, SchemaHandler[] handlers) {
        if (item == null) return;

        item.readOperationsMap().forEach((methodEnum, op) -> {
            String method = methodEnum.toString();
            String opName = Optional.ofNullable(op.getOperationId())
                    .orElse(method + " " + url);

            Optional.ofNullable(op.getParameters()).orElse(List.of()).forEach(p -> {
                if (p.getSchema() != null) {
                    Deque<String> path = new ArrayDeque<>(List.of(opName, "param", p.getName()));
                    dfs(p.getSchema(), api, path, visited, handlers, url, method);
                }
            });

            Optional.ofNullable(op.getRequestBody())
                    .map(RequestBody::getContent)
                    .ifPresent(content ->
                            content.forEach((mt, media) -> {
                                Deque<String> path = new ArrayDeque<>(List.of(opName, "request", mt));
                                consumeMedia(media, api, path, visited, handlers, url, method);
                            }));

            op.getResponses().forEach((code, resp) ->
                    Optional.ofNullable(resp.getContent()).ifPresent(content ->
                            content.forEach((mt, media) -> {
                                Deque<String> p = new ArrayDeque<>(List.of(opName, "response" + code, mt));
                                consumeMedia(media, api, p, visited, handlers, url, method);
                            })));
        });
    }

    private static void consumeMedia(MediaType media, OpenAPI api, Deque<String> path,
                                     Set<Schema<?>> visited, SchemaHandler[] handlers,
                                     String url, String method) {
        if (media != null && media.getSchema() != null) {
            dfs(media.getSchema(), api, path, visited, handlers, url, method);
        }
    }

    /**
     * Depth-first traversal with cycle detection + context propagation.
     */
    private static void dfs(Schema<?> schema, OpenAPI api, Deque<String> path,
                            Set<Schema<?>> visited, SchemaHandler[] handlers,
                            String url, String method) {

        if (schema == null || !visited.add(schema)) return;

        if (schema.get$ref() != null) {
            String refName = CatsModelUtils.getSimpleRefUsingOAT(schema.get$ref());
            Schema<?> target = Optional.ofNullable(api.getComponents())
                    .map(Components::getSchemas)
                    .map(m -> m.get(refName))
                    .orElse(null);
            dfs(target, api, path, visited, handlers, url, method);
            return;
        }

        String fqn = String.join(".", path);
        SchemaLocation loc = new SchemaLocation(url, method, fqn);
        for (SchemaHandler h : handlers) {
            h.handle(loc, schema);
        }

        if (CatsModelUtils.isComposedSchema(schema)) {
            Stream.of(schema.getAllOf(), schema.getAnyOf(), schema.getOneOf())
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .forEach(sub -> dfs(sub, api, new ArrayDeque<>(path), visited,
                            handlers, url, method));
        }

        if (schema.getProperties() != null) {
            schema.getProperties().forEach((prop, sub) -> {
                Deque<String> next = new ArrayDeque<>(path);
                next.add(prop);
                dfs(sub, api, next, visited, handlers, url, method);
            });
        }

        if (schema.getItems() != null) {
            Deque<String> next = new ArrayDeque<>(path);
            next.add("[]");
            dfs(schema.getItems(), api, next, visited, handlers, url, method);
        }

        if (schema.getAdditionalProperties() instanceof Schema<?> ap) {
            Deque<String> next = new ArrayDeque<>(path);
            next.add("additionalProperties");
            dfs(ap, api, next, visited, handlers, url, method);
        }

        if (schema.getNot() != null) {
            dfs(schema.getNot(), api, new ArrayDeque<>(path), visited, handlers, url, method);
        }
    }
}