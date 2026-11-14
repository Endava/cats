package com.endava.cats.report.error.fields;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Reads the error type and the messages of a JSON error object, by the roles of its fields
 * ({@link ErrorFieldRoles}). The messages come from the first of these that has any:
 * <ol>
 *     <li>a code array and a message array of the same size, zipped ({@code codes[i]: descriptions[i]});</li>
 *     <li>the structured messages of the common frameworks: string arrays, arrays of objects with a message and a
 *     location, and maps of field to messages ({@code errors}, {@code messages}, {@code detail}...);</li>
 *     <li>a map of field to messages that is the whole body (Django REST framework);</li>
 *     <li>every distinct message-role field: strings, arrays, and objects, which contribute their own message and
 *     location.</li>
 * </ol>
 * Every message is rendered as {@code location: message}, or as {@code code: message} for an item that has a code
 * and no location.
 */
public final class ErrorFieldReader {
    private static final List<String> STRUCTURED_KEYS = List.of("errors", "messages", "message", "detail", "details",
            "violations", "fieldErrors", "causes", "invalid-params", "invalidParams", "validationErrors");
    private static final List<String> ITEM_MESSAGE_KEYS = List.of("msg", "message", "defaultMessage", "detail", "description",
            "reason", "title", "code");
    private static final List<String> ITEM_LOCATION_KEYS = List.of("loc", "field", "path", "property", "propertyPath",
            "param", "parameter", "pointer");
    private static final Pattern POINTER_PREFIX = Pattern.compile("^#?/*");
    private static final int MAX_MESSAGES = 20;

    private final ErrorFieldRoles roles;

    public ErrorFieldReader(ErrorFieldRoles roles) {
        this.roles = roles;
    }

    @SafeVarargs
    public static List<String> firstNonEmpty(Supplier<List<String>>... candidates) {
        return Stream.of(candidates).map(Supplier::get).filter(messages -> !messages.isEmpty()).findFirst().orElse(List.of());
    }

    public ErrorTypeFields.ErrorType type(JsonObject obj) {
        return ErrorTypeFields.choose(obj, roles);
    }

    public List<String> messages(JsonObject obj) {
        return firstNonEmpty(() -> parallelMessages(obj), () -> structuredMessages(obj), () -> fieldMapMessages(obj),
                () -> roleMessages(obj));
    }

    /**
     * An object without any known error field is described by its leaves in document order
     * ({@code msg: Bad credentials; status: BAD_REQUEST}), so its values are masked like message text instead of as
     * quoted JSON strings. Fields the contract declares variable are left out.
     */
    public String leaves(JsonObject obj) {
        return leaves("", obj).limit(MAX_MESSAGES).collect(Collectors.joining("; "));
    }

    private Stream<String> leaves(String path, JsonElement element) {
        if (element.isJsonObject()) {
            return element.getAsJsonObject().entrySet().stream().filter(entry -> !roles.has(entry.getKey(), ErrorFieldRole.VARIABLE))
                    .flatMap(entry -> leaves(path.isEmpty() ? entry.getKey() : path + "." + entry.getKey(), entry.getValue()));
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().asList().stream().flatMap(item -> leaves(path + "[]", item));
        }
        return Stream.of(path + ": " + (element.isJsonNull() ? "null" : element.getAsString()));
    }

    private List<String> parallelMessages(JsonObject obj) {
        List<String> codes = primitiveArray(obj, ErrorFieldRole.TYPE);
        List<String> texts = primitiveArray(obj, ErrorFieldRole.MESSAGE);
        if (codes.isEmpty() || codes.size() != texts.size()) {
            return List.of();
        }
        return IntStream.range(0, codes.size()).mapToObj(index -> codes.get(index) + ": " + texts.get(index)).limit(MAX_MESSAGES).toList();
    }

    private List<String> primitiveArray(JsonObject obj, ErrorFieldRole role) {
        return obj.entrySet().stream().filter(entry -> roles.has(entry.getKey(), role)).map(Map.Entry::getValue)
                .filter(JsonElement::isJsonArray).map(value -> primitives(value.getAsJsonArray()))
                .filter(values -> !values.isEmpty()).findFirst().orElse(List.of());
    }

    private static List<String> primitives(JsonArray array) {
        boolean allPrimitives = array.asList().stream().allMatch(JsonElement::isJsonPrimitive);
        return allPrimitives ? array.asList().stream().map(JsonElement::getAsString).filter(StringUtils::isNotBlank).toList() : List.of();
    }

    private List<String> structuredMessages(JsonObject obj) {
        return STRUCTURED_KEYS.stream().map(obj::get).map(this::structuredMessages)
                .filter(messages -> !messages.isEmpty()).findFirst().orElse(List.of());
    }

    private List<String> structuredMessages(JsonElement value) {
        if (value != null && value.isJsonArray()) {
            return arrayMessages(value.getAsJsonArray());
        }
        return value != null && value.isJsonObject() ? fieldMapMessages(value.getAsJsonObject()) : List.of();
    }

    public List<String> arrayMessages(JsonArray array) {
        return array.asList().stream().map(this::itemMessage).filter(StringUtils::isNotBlank).limit(MAX_MESSAGES).toList();
    }

    private List<String> roleMessages(JsonObject obj) {
        return obj.entrySet().stream().filter(entry -> roles.has(entry.getKey(), ErrorFieldRole.MESSAGE))
                .flatMap(entry -> valueMessages(entry.getValue())).map(String::strip).filter(StringUtils::isNotEmpty)
                .distinct().limit(MAX_MESSAGES).toList();
    }

    private Stream<String> valueMessages(JsonElement value) {
        if (value.isJsonArray()) {
            return arrayMessages(value.getAsJsonArray()).stream();
        }
        return value.isJsonPrimitive() || value.isJsonObject() ? Stream.of(itemMessage(value)) : Stream.empty();
    }

    private String itemMessage(JsonElement item) {
        if (item.isJsonPrimitive()) {
            return item.getAsString();
        }
        return item.isJsonObject() ? objectMessage(item.getAsJsonObject()) : "";
    }

    private String objectMessage(JsonObject obj) {
        String direct = directMessage(obj);
        String message = direct.isEmpty() ? nestedMessage(obj) : direct;
        String label = StringUtils.defaultIfEmpty(location(obj), code(obj));
        return label.isEmpty() || message.isEmpty() || label.equals(message) ? message : label + ": " + message;
    }

    private String directMessage(JsonObject obj) {
        String known = firstPrimitive(obj, ITEM_MESSAGE_KEYS);
        return known.isEmpty() ? firstPrimitive(obj, fieldsWithRole(obj, ErrorFieldRole.MESSAGE)) : known;
    }

    private String nestedMessage(JsonObject obj) {
        return obj.asMap().values().stream().filter(JsonElement::isJsonObject).map(this::itemMessage)
                .filter(StringUtils::isNotBlank).findFirst().orElse("");
    }

    private String code(JsonObject obj) {
        List<String> codeFields = fieldsWithRole(obj, ErrorFieldRole.TYPE).stream()
                .filter(field -> ErrorFieldRoleResolver.lastWord(field).startsWith("code"))
                .filter(field -> !ErrorFieldRoleResolver.isTransportCode(field)).toList();
        return firstPrimitive(obj, codeFields);
    }

    private List<String> fieldsWithRole(JsonObject obj, ErrorFieldRole role) {
        return obj.keySet().stream().filter(field -> roles.has(field, role)).toList();
    }

    public static String firstPrimitive(JsonObject obj, List<String> keys) {
        return keys.stream().map(obj::get).filter(value -> value != null && value.isJsonPrimitive())
                .map(JsonElement::getAsString).filter(StringUtils::isNotBlank).findFirst().orElse("");
    }

    /**
     * A location such as FastAPI's {@code ["body", "name"]} or the JSON pointer {@code #/profile/color} becomes a
     * dotted path ({@code body.name}, {@code profile.color}), whose last segment the masker recognizes as a field name.
     */
    private static String location(JsonObject item) {
        return ITEM_LOCATION_KEYS.stream().map(item::get).filter(value -> value != null && !value.isJsonNull())
                .map(ErrorFieldReader::dottedPath).filter(StringUtils::isNotBlank).findFirst().orElse("");
    }

    private static String dottedPath(JsonElement location) {
        if (location.isJsonArray()) {
            return location.getAsJsonArray().asList().stream().filter(JsonElement::isJsonPrimitive)
                    .map(JsonElement::getAsString).collect(Collectors.joining("."));
        }
        return location.isJsonPrimitive() ? POINTER_PREFIX.matcher(location.getAsString()).replaceFirst("").replace('/', '.') : "";
    }

    private static List<String> fieldMapMessages(JsonObject obj) {
        return isFieldMap(obj) ? fieldMapMessages("", obj).limit(MAX_MESSAGES).toList() : List.of();
    }

    private static Stream<String> fieldMapMessages(String prefix, JsonObject obj) {
        return obj.entrySet().stream()
                .flatMap(entry -> fieldMessages(prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey(), entry.getValue()));
    }

    private static Stream<String> fieldMessages(String field, JsonElement value) {
        if (value.isJsonObject()) {
            return fieldMapMessages(field, value.getAsJsonObject());
        }
        JsonArray messages = value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
        return messages.asList().stream().map(JsonElement::getAsString).map(message -> field.isEmpty() ? message : field + ": " + message);
    }

    /**
     * A map of field names to message arrays, possibly nested ({@code {"address": {"city": ["required"]}}}).
     */
    private static boolean isFieldMap(JsonObject obj) {
        return !obj.isEmpty() && obj.entrySet().stream().map(Map.Entry::getValue).allMatch(ErrorFieldReader::isFieldMessages);
    }

    private static boolean isFieldMessages(JsonElement value) {
        if (value.isJsonObject()) {
            return isFieldMap(value.getAsJsonObject());
        }
        return value.isJsonArray() && !value.getAsJsonArray().isEmpty()
                && value.getAsJsonArray().asList().stream().allMatch(item -> item.isJsonPrimitive() && item.getAsJsonPrimitive().isString());
    }
}
