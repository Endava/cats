package com.endava.cats.report.error.contract;

import com.endava.cats.report.error.fields.ErrorFieldRole;
import com.endava.cats.report.error.fields.ErrorFieldRoleResolver;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the roles of the properties an error schema declares. Roles follow {@link ErrorFieldRoleResolver}, with two
 * additions only a contract can make:
 * <ul>
 *     <li>properties of format {@code uuid}, {@code date} or {@code date-time}, and names such as {@code requestId},
 *     {@code correlationId}, {@code traceId}, {@code timestamp}, {@code instance}, are {@link ErrorFieldRole#VARIABLE};</li>
 *     <li>when no declared property has a message role, the declared string properties without role (that are not
 *     titles, errors or status codes) are the messages: the contract says they describe the error.</li>
 * </ul>
 * {@code $ref} and {@code allOf}/{@code oneOf}/{@code anyOf} are resolved; properties are read down to a fixed depth
 * and up to a fixed number, so a huge schema never slows reporting down.
 */
@SuppressWarnings("rawtypes")
public final class ErrorSchemaFields {
    private static final int MAX_DEPTH = 4;
    private static final int MAX_FIELDS = 256;
    private static final Set<String> VARIABLE_FORMATS = Set.of("uuid", "date", "date-time");
    private static final Set<String> VARIABLE_NAMES = Set.of("requestid", "correlationid", "traceid", "spanid", "timestamp",
            "instance", "path", "url", "uri");
    private static final Set<String> FREE_TEXT_FIELDS = Set.of("title", "error");

    private final Map<String, Schema> componentSchemas;

    ErrorSchemaFields(Map<String, Schema> componentSchemas) {
        this.componentSchemas = componentSchemas;
    }

    private record DeclaredField(String name, Optional<ErrorFieldRole> role, boolean plainString) {
    }

    /**
     * Builds the contract of an error schema.
     *
     * @param errorSchema the schema of the error response body
     * @return the contract, or {@link ErrorResponseContract#NONE} for a schema without properties
     */
    ErrorResponseContract contractOf(Schema<?> errorSchema) {
        Map<String, Schema> topLevel = properties(errorSchema, 0);
        if (topLevel.isEmpty()) {
            return ErrorResponseContract.NONE;
        }
        List<DeclaredField> declared = fields(errorSchema, 0).limit(MAX_FIELDS).toList();
        return new ErrorResponseContract(Set.copyOf(topLevel.keySet()), Set.copyOf(required(errorSchema, 0)), roles(declared));
    }

    private Stream<DeclaredField> fields(Schema<?> schema, int depth) {
        return properties(schema, 0).entrySet().stream().flatMap(entry -> declaredWithChildren(entry.getKey(), entry.getValue(), depth));
    }

    private Stream<DeclaredField> declaredWithChildren(String name, Schema<?> property, int depth) {
        Stream<DeclaredField> children = depth < MAX_DEPTH ? fields(element(property), depth + 1) : Stream.empty();
        return Stream.concat(Stream.of(declared(name, property)), children);
    }

    private Schema<?> element(Schema<?> property) {
        return property != null && property.getItems() != null ? resolve(property.getItems()) : property;
    }

    private Map<String, Schema> properties(Schema<?> schema, int depth) {
        Schema<?> resolved = resolve(schema);
        if (resolved == null || depth > MAX_DEPTH) {
            return Map.of();
        }
        Stream<Map.Entry<String, Schema>> own = Optional.ofNullable(resolved.getProperties()).stream()
                .flatMap(properties -> properties.entrySet().stream());
        Stream<Map.Entry<String, Schema>> composed = composedParts(resolved)
                .flatMap(part -> properties(part, depth + 1).entrySet().stream());
        return Stream.concat(own, composed).collect(Collectors.toMap(Map.Entry::getKey, entry -> resolve(entry.getValue()),
                (first, _) -> first, LinkedHashMap::new));
    }

    private List<String> required(Schema<?> schema, int depth) {
        Schema<?> resolved = resolve(schema);
        if (resolved == null || depth > MAX_DEPTH) {
            return List.of();
        }
        Stream<String> inherited = Optional.ofNullable(resolved.getAllOf()).stream().flatMap(List::stream)
                .flatMap(part -> required(part, depth + 1).stream());
        return Stream.concat(Optional.ofNullable(resolved.getRequired()).stream().flatMap(List::stream), inherited).distinct().toList();
    }

    private static Stream<Schema> composedParts(Schema<?> schema) {
        return Stream.of(schema.getAllOf(), schema.getOneOf(), schema.getAnyOf()).filter(parts -> parts != null).flatMap(List::stream);
    }

    private Schema<?> resolve(Schema<?> schema) {
        return CatsModelUtils.resolveSchema(schema, componentSchemas);
    }

    private static DeclaredField declared(String name, Schema<?> schema) {
        if (isVariable(name, schema)) {
            return new DeclaredField(name, Optional.of(ErrorFieldRole.VARIABLE), false);
        }
        Optional<ErrorFieldRole> role = ErrorFieldRoleResolver.BY_NAME.roleOf(name);
        return new DeclaredField(name, role, role.isEmpty() && isDescriptiveString(name, schema));
    }

    private static boolean isVariable(String name, Schema<?> schema) {
        boolean variableFormat = schema != null && VARIABLE_FORMATS.contains(String.valueOf(schema.getFormat()));
        return variableFormat || VARIABLE_NAMES.contains(name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT));
    }

    private static boolean isDescriptiveString(String name, Schema<?> schema) {
        return CatsModelUtils.isStringSchema(schema) && !FREE_TEXT_FIELDS.contains(name) && !ErrorFieldRoleResolver.isTransportCode(name);
    }

    private static Map<String, ErrorFieldRole> roles(List<DeclaredField> declared) {
        Map<String, ErrorFieldRole> roles = declared.stream()
                .flatMap(field -> field.role().map(role -> Map.entry(field.name(), role)).stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first, LinkedHashMap::new));
        return roles.containsValue(ErrorFieldRole.MESSAGE) ? roles : withPlainStringsAsMessages(roles, declared);
    }

    private static Map<String, ErrorFieldRole> withPlainStringsAsMessages(Map<String, ErrorFieldRole> roles, List<DeclaredField> declared) {
        Map<String, ErrorFieldRole> all = new LinkedHashMap<>(roles);
        declared.stream().filter(DeclaredField::plainString).forEach(field -> all.putIfAbsent(field.name(), ErrorFieldRole.MESSAGE));
        return all;
    }
}
