package com.endava.cats.openapi.handler.api;

import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.JsonUtils;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deep-schema walker producing:
 * • FQN — human-readable.
 * • Pointer — strict RFC-6901, so it matches the YAML indexer.
 */
@Singleton
public class SchemaWalker {
    private final Instance<SchemaHandler> handlers;

    /**
     * Constructs a SchemaWalker with the provided schema handlers.
     *
     * @param handlers the schema handlers to be used for processing schemas
     */
    public SchemaWalker(Instance<SchemaHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * Initializes the schema handlers for the provided OpenAPI document.
     * <p>
     * This method collects all schema handlers and applies them to the OpenAPI document.
     *
     * @param api the OpenAPI document to initialize handlers for
     */
    public void initHandlers(OpenAPI api) {
        walk(api, handlers.stream().toArray(SchemaHandler[]::new));
    }

    private static String toFqn(Deque<String> d) {
        return String.join(".", d);
    }

    /**
     * Walks the OpenAPI schema and applies handlers to each schema found.
     * <p>
     * This method traverses the OpenAPI document, including components and paths,
     * and applies the provided handlers to each schema encountered.
     *
     * @param api      the OpenAPI document to walk
     * @param handlers the handlers to apply to each schema
     */
    public static void walk(OpenAPI api, SchemaHandler... handlers) {
        Objects.requireNonNull(api);
        Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        Optional.ofNullable(api.getComponents())
                .map(Components::getSchemas)
                .ifPresent(schemas -> schemas.forEach((name, schema) -> {
                    Deque<String> fqn = new ArrayDeque<>(List.of(name));
                    Deque<String> ptr = new ArrayDeque<>(
                            List.of("components", "schemas", JsonUtils.escape(name)));
                    dfs(schema, api, fqn, ptr, visited, handlers, null, null);
                }));

        Optional.ofNullable(api.getPaths())
                .ifPresent(paths -> paths.forEach((url, item) ->
                        processPathItem(item, api, url, visited, handlers)));
    }

    /**
     * Processes a PathItem and applies schema handlers to its operations.
     * <p>
     * This method iterates through the operations of a PathItem, extracting parameters,
     * request bodies, and responses, and applies the provided handlers to each schema found.
     *
     * @param item     the PathItem to process
     * @param api      the OpenAPI document containing the PathItem
     * @param rawUrl   the raw URL of the PathItem
     * @param visited  a set of visited schemas to avoid processing duplicates
     * @param handlers the handlers to apply to each schema found
     */
    private static void processPathItem(PathItem item, OpenAPI api,
                                        String rawUrl, Set<Schema<?>> visited,
                                        SchemaHandler[] handlers) {
        if (item == null) return;
        String urlEsc = JsonUtils.escape(rawUrl);

        item.readOperationsMap().forEach((m, op) -> {
            String method = m.toString().toLowerCase();
            String opName = Optional.ofNullable(op.getOperationId())
                    .orElse(m + " " + rawUrl);

            Deque<String> fqnStem = new ArrayDeque<>(List.of(opName));

            Deque<String> ptrStem = new ArrayDeque<>(
                    List.of("paths", urlEsc, method));

            List<Parameter> params = Optional.ofNullable(op.getParameters())
                    .orElse(List.of());
            for (int i = 0; i < params.size(); i++) {
                Parameter p = params.get(i);
                if (p.getSchema() == null) continue;

                Deque<String> fqn = new ArrayDeque<>(fqnStem);
                fqn.add("param");
                fqn.add(p.getName());

                Deque<String> ptr = new ArrayDeque<>(ptrStem);
                ptr.add("parameters");
                ptr.add(Integer.toString(i));
                ptr.add("schema");

                dfs(p.getSchema(), api, fqn, ptr, visited, handlers, rawUrl, m.toString());
            }

            Optional.ofNullable(op.getRequestBody()).map(RequestBody::getContent)
                    .ifPresent(content -> content.forEach((mt, media) -> {
                        Deque<String> fqn = new ArrayDeque<>(fqnStem);
                        fqn.add("request");
                        fqn.add(mt);

                        Deque<String> ptr = new ArrayDeque<>(ptrStem);
                        ptr.add("requestBody");
                        ptr.add("content");
                        ptr.add(mt);
                        ptr.add("schema");

                        consumeMedia(media, api, fqn, ptr, visited, handlers, rawUrl, m.toString());
                    }));

            op.getResponses().forEach((code, resp) ->
                    Optional.ofNullable(resp.getContent()).ifPresent(content ->
                            content.forEach((mt, media) -> {
                                Deque<String> fqn = new ArrayDeque<>(fqnStem);
                                fqn.add("response" + code);
                                fqn.add(mt);

                                Deque<String> ptr = new ArrayDeque<>(ptrStem);
                                ptr.add("responses");
                                ptr.add(code);
                                ptr.add("content");
                                ptr.add(mt);
                                ptr.add("schema");

                                consumeMedia(media, api, fqn, ptr, visited, handlers,
                                        rawUrl, m.toString());
                            })));
        });
    }

    /**
     * Consumes a MediaType schema and applies schema handlers to it.
     * <p>
     * This method checks if the MediaType has a schema and applies the provided handlers
     * to the schema, using the provided FQN and pointer.
     *
     * @param media    the MediaType to consume
     * @param api      the OpenAPI document containing the MediaType
     * @param fqn      the fully qualified name deque
     * @param ptr      the pointer deque
     * @param visited  a set of visited schemas to avoid processing duplicates
     * @param handlers the handlers to apply to the schema found in the MediaType
     * @param url      the URL of the operation
     * @param method   the HTTP method of the operation
     */
    private static void consumeMedia(MediaType media, OpenAPI api,
                                     Deque<String> fqn, Deque<String> ptr,
                                     Set<Schema<?>> visited,
                                     SchemaHandler[] handlers,
                                     String url, String method) {
        if (media != null && media.getSchema() != null) {
            dfs(media.getSchema(), api, fqn, ptr, visited, handlers, url, method);
        }
    }

    /**
     * Recursively traverses a schema and applies handlers to it.
     * <p>
     * This method handles references, composed schemas, object properties, array items,
     * additional properties, and the "not" schema. It uses depth-first search to traverse
     * the schema tree and applies the provided handlers to each schema found.
     *
     * @param schema   the schema to traverse
     * @param api      the OpenAPI document containing the schema
     * @param fqn      the fully qualified name deque
     * @param ptr      the pointer deque
     * @param visited  a set of visited schemas to avoid processing duplicates
     * @param handlers the handlers to apply to each schema found
     * @param url      the URL of the operation
     * @param method   the HTTP method of the operation
     */
    private static void dfs(Schema<?> schema, OpenAPI api,
                            Deque<String> fqn, Deque<String> ptr,
                            Set<Schema<?>> visited, SchemaHandler[] handlers,
                            String url, String method) {

        if (schema == null || !visited.add(schema)) return;

        if (schema.get$ref() != null) {
            String refName = CatsModelUtils.getSimpleRefUsingOAT(schema.get$ref());
            Schema<?> target = Optional.ofNullable(api.getComponents())
                    .map(Components::getSchemas).map(m -> m.get(refName))
                    .orElse(null);
            dfs(target, api, fqn, ptr, visited, handlers, url, method);
            return;
        }

        SchemaLocation loc = new SchemaLocation(
                url, method, toFqn(fqn), JsonUtils.toPointer(ptr));
        for (SchemaHandler h : handlers) h.handle(loc, schema);

        if (CatsModelUtils.isComposedSchema(schema)) {
            Map<String, List<Schema>> composed = Map.of(
                    "allOf", Optional.ofNullable(schema.getAllOf()).orElse(List.of()),
                    "anyOf", Optional.ofNullable(schema.getAnyOf()).orElse(List.of()),
                    "oneOf", Optional.ofNullable(schema.getOneOf()).orElse(List.of()));
            composed.forEach((tag, list) -> {
                for (int i = 0; i < list.size(); i++) {
                    Deque<String> fqnN = new ArrayDeque<>(fqn);
                    Deque<String> ptrN = new ArrayDeque<>(ptr);
                    ptrN.add(tag);
                    ptrN.add(Integer.toString(i));
                    dfs(list.get(i), api, fqnN, ptrN, visited, handlers, url, method);
                }
            });
        }

        if (schema.getProperties() != null) {
            schema.getProperties().forEach((prop, sub) -> {
                Deque<String> fqnN = new ArrayDeque<>(fqn);
                fqnN.add(prop);

                Deque<String> ptrN = new ArrayDeque<>(ptr);
                ptrN.add("properties");
                ptrN.add(JsonUtils.escape(prop));

                dfs(sub, api, fqnN, ptrN, visited, handlers, url, method);
            });
        }

        if (schema.getItems() != null) {
            Deque<String> fqnN = new ArrayDeque<>(fqn);
            fqnN.add("[]");
            Deque<String> ptrN = new ArrayDeque<>(ptr);
            ptrN.add("items");
            dfs(schema.getItems(), api, fqnN, ptrN, visited, handlers, url, method);
        }

        if (schema.getAdditionalProperties() instanceof Schema<?> ap) {
            Deque<String> fqnN = new ArrayDeque<>(fqn);
            fqnN.add("additionalProperties");
            Deque<String> ptrN = new ArrayDeque<>(ptr);
            ptrN.add("additionalProperties");
            dfs(ap, api, fqnN, ptrN, visited, handlers, url, method);
        }

        if (schema.getNot() != null) {
            Deque<String> ptrN = new ArrayDeque<>(ptr);
            ptrN.add("not");
            dfs(schema.getNot(), api, new ArrayDeque<>(fqn), ptrN,
                    visited, handlers, url, method);
        }
    }
}