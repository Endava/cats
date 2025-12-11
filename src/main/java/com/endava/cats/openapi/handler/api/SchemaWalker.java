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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
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
        WalkContext ctx = new WalkContext(api, Collections.newSetFromMap(new IdentityHashMap<>()), handlers, null, null);

        Optional.ofNullable(api.getComponents())
                .map(Components::getSchemas)
                .ifPresent(schemas -> schemas.forEach((name, schema) -> {
                    Deque<String> fqn = new ArrayDeque<>(List.of(name));
                    Deque<String> ptr = new ArrayDeque<>(
                            List.of("components", "schemas", JsonUtils.escape(name)));
                    dfs(schema, fqn, ptr, ctx);
                }));

        Optional.ofNullable(api.getPaths())
                .ifPresent(paths -> paths.forEach((url, item) ->
                        processPathItem(item, url, ctx)));
    }

    /**
     * Processes a PathItem and applies schema handlers to its operations.
     *
     * @param item   the PathItem to process
     * @param rawUrl the raw URL of the PathItem
     * @param ctx    the walk context
     */
    private static void processPathItem(PathItem item, String rawUrl, WalkContext ctx) {
        if (item == null) return;
        String urlEsc = JsonUtils.escape(rawUrl);

        item.readOperationsMap().forEach((m, op) -> {
            String method = m.toString().toLowerCase(Locale.ROOT);
            String opName = Optional.ofNullable(op.getOperationId())
                    .orElse(m + " " + rawUrl);

            Deque<String> fqnStem = new ArrayDeque<>(List.of(opName));
            Deque<String> ptrStem = new ArrayDeque<>(List.of("paths", urlEsc, method));
            WalkContext opCtx = ctx.withLocation(rawUrl, m.toString());

            List<Parameter> params = Optional.ofNullable(op.getParameters()).orElse(List.of());
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

                dfs(p.getSchema(), fqn, ptr, opCtx);
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

                        consumeMedia(media, fqn, ptr, opCtx);
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

                                consumeMedia(media, fqn, ptr, opCtx);
                            })));
        });
    }

    /**
     * Consumes a MediaType schema and applies schema handlers to it.
     *
     * @param media the MediaType to consume
     * @param fqn   the fully qualified name deque
     * @param ptr   the pointer deque
     * @param ctx   the walk context
     */
    private static void consumeMedia(MediaType media, Deque<String> fqn, Deque<String> ptr, WalkContext ctx) {
        if (media != null && media.getSchema() != null) {
            dfs(media.getSchema(), fqn, ptr, ctx);
        }
    }

    /**
     * Recursively traverses a schema and applies handlers to it.
     *
     * @param schema the schema to traverse
     * @param fqn    the fully qualified name deque
     * @param ptr    the pointer deque
     * @param ctx    the walk context
     */
    private static void dfs(Schema<?> schema, Deque<String> fqn, Deque<String> ptr, WalkContext ctx) {
        if (schema == null || !ctx.getVisited().add(schema)) return;

        if (schema.get$ref() != null) {
            String refName = CatsModelUtils.getSimpleRefUsingOAT(schema.get$ref());
            Schema<?> target = Optional.ofNullable(ctx.getApi().getComponents())
                    .map(Components::getSchemas).map(m -> m.get(refName))
                    .orElse(null);
            dfs(target, fqn, ptr, ctx);
            return;
        }

        SchemaLocation loc = new SchemaLocation(ctx.getUrl(), ctx.getMethod(), toFqn(fqn), JsonUtils.toPointer(ptr));
        for (SchemaHandler h : ctx.getHandlers()) h.handle(loc, schema);

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
                    dfs(list.get(i), fqnN, ptrN, ctx);
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

                dfs(sub, fqnN, ptrN, ctx);
            });
        }

        if (schema.getItems() != null) {
            Deque<String> fqnN = new ArrayDeque<>(fqn);
            fqnN.add("[]");
            Deque<String> ptrN = new ArrayDeque<>(ptr);
            ptrN.add("items");
            dfs(schema.getItems(), fqnN, ptrN, ctx);
        }

        if (schema.getAdditionalProperties() instanceof Schema<?> ap) {
            Deque<String> fqnN = new ArrayDeque<>(fqn);
            fqnN.add("additionalProperties");
            Deque<String> ptrN = new ArrayDeque<>(ptr);
            ptrN.add("additionalProperties");
            dfs(ap, fqnN, ptrN, ctx);
        }

        if (schema.getNot() != null) {
            Deque<String> ptrN = new ArrayDeque<>(ptr);
            ptrN.add("not");
            dfs(schema.getNot(), new ArrayDeque<>(fqn), ptrN, ctx);
        }
    }
}

/**
 * Encapsulates the traversal context for schema walking.
 */
@Data
@EqualsAndHashCode
@ToString
final class WalkContext {
    private final OpenAPI api;
    private final Set<Schema<?>> visited;
    private final SchemaHandler[] handlers;
    private final String url;
    private final String method;

    WalkContext withLocation(String newUrl, String newMethod) {
        return new WalkContext(api, visited, handlers, newUrl, newMethod);
    }

}