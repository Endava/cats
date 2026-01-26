package com.endava.cats.util.external;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal, plain-Java replacement for Guava's com.google.common.net.MediaType.
 * <p>
 * Supports: create(), parse(), type(), subtype(), charset(), withCharset(),
 * withParameter(), withoutParameters(), hasWildcard(), toString(), equals/hashCode.
 * <p>
 * Notes:
 * - Parameter attribute names are normalized to lowercase (like Guava).
 * - type/subtype are normalized to lowercase.
 * - charset parameter value is normalized to lowercase (like Guava).
 * - This is intentionally not a full RFC 2045/2046 validator.
 */
public final class MediaType {

    private final String type;
    private final String subtype;
    // keep insertion order for stable toString()
    private final Map<String, String> parameters;

    private MediaType(String type, String subtype, Map<String, String> parameters) {
        this.type = normalizeToken(type);
        this.subtype = normalizeToken(subtype);
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    /**
     * Creates a new media type with the given type and subtype.
     *
     * @param type    the media type
     * @param subtype the media subtype
     * @return the media type
     */
    public static MediaType create(String type, String subtype) {
        return new MediaType(type, subtype, Map.of());
    }

    /**
     * Parses a media type string into a MediaType object.
     * <p>
     * Supports parsing of:
     * - Simple media types: "application/json"
     * - Media types with charset: "application/json; charset=UTF-8"
     * - Media types with multiple parameters: "text/plain; charset=\"utf-8\"; format=flowed"
     * <p>
     * Parameter names and values are normalized according to HTTP standards.
     * Type and subtype are converted to lowercase.
     *
     * @param input the media type string to parse
     * @return the parsed MediaType object
     * @throws NullPointerException     if input is null
     * @throws IllegalArgumentException if input is empty or malformed
     */
    public static MediaType parse(String input) {
        Objects.requireNonNull(input, "input");
        String s = input.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Invalid media type: empty");

        String[] parts = s.split(";", 30);
        String typePart = parts[0].trim();

        int slash = typePart.indexOf('/');
        if (slash <= 0 || slash == typePart.length() - 1) {
            throw new IllegalArgumentException("Invalid media type (missing type/subtype): " + input);
        }

        String type = typePart.substring(0, slash).trim();
        String subtype = typePart.substring(slash + 1).trim();

        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) {
                continue;
            }

            int eq = p.indexOf('=');
            if (eq <= 0 || eq == p.length() - 1) {
                // ignore invalid param segments rather than failing hard
                continue;
            }

            String attr = normalizeToken(p.substring(0, eq).trim());
            String value = stripQuotes(p.substring(eq + 1).trim());

            if ("charset".equals(attr)) {
                value = value.toLowerCase(Locale.ROOT);
            }

            // This minimal version keeps only one value per attribute (common HTTP usage).
            params.put(attr, value);
        }

        return new MediaType(type, subtype, params);
    }

    /**
     * Returns the primary type of this media type.
     * <p>
     * For example, "application" in "application/json".
     *
     * @return the primary type, normalized to lowercase
     */
    public String type() {
        return type;
    }

    /**
     * Returns the subtype of this media type.
     * <p>
     * For example, "json" in "application/json".
     *
     * @return the subtype, normalized to lowercase
     */
    public String subtype() {
        return subtype;
    }

    /**
     * Checks if this media type contains a wildcard in either the type or subtype.
     * <p>
     * Examples of wildcards:
     * <ul>
     * <li>"&#42;/&#42;" (matches any type)</li>
     * <li>"text/&#42;" (matches any text subtype)</li>
     * </ul>
     *
     * @return true if type or subtype is "&#42;", false otherwise
     */
    public boolean hasWildcard() {
        return "*".equals(type) || "*".equals(subtype);
    }

    /**
     * Returns the charset parameter of this media type, if present and valid.
     * <p>
     * If the charset parameter is not present, blank, or refers to an unknown/invalid
     * charset name, returns an empty Optional.
     *
     * @return an Optional containing the Charset if valid, or empty otherwise
     */
    public Optional<Charset> charset() {
        String cs = parameters.get("charset");
        if (cs == null || cs.isBlank()) return Optional.empty();
        try {
            return Optional.of(Charset.forName(cs));
        } catch (Exception _) {
            // unknown/invalid charset => behave safely
            return Optional.empty();
        }
    }

    /**
     * Returns a new MediaType with the specified charset parameter.
     * <p>
     * The charset name is normalized to lowercase.
     *
     * @param charset the charset to set
     * @return a new MediaType with the charset parameter
     * @throws NullPointerException if charset is null
     */
    public MediaType withCharset(Charset charset) {
        Objects.requireNonNull(charset, "charset");
        return withParameter("charset", charset.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Returns a new MediaType with the specified parameter added or replaced.
     * <p>
     * Parameter names are normalized to lowercase. If the parameter already exists,
     * its value is replaced. The charset parameter value is also normalized to lowercase.
     *
     * @param attribute the parameter name
     * @param value     the parameter value
     * @return a new MediaType with the parameter
     * @throws NullPointerException if attribute or value is null
     */
    public MediaType withParameter(String attribute, String value) {
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(value, "value");

        Map<String, String> copy = new LinkedHashMap<>(this.parameters);
        String attr = normalizeToken(attribute);
        String val = stripQuotes(value.trim());

        if ("charset".equals(attr)) {
            val = val.toLowerCase(Locale.ROOT);
        }

        copy.put(attr, val);
        return new MediaType(type, subtype, copy);
    }

    /**
     * Returns a new MediaType with all parameters removed.
     * <p>
     * If this media type has no parameters, returns this instance.
     *
     * @return a MediaType without parameters
     */
    public MediaType withoutParameters() {
        if (parameters.isEmpty()) return this;
        return new MediaType(type, subtype, Map.of());
    }

    /**
     * Returns an unmodifiable map of all parameters.
     * <p>
     * Parameter names are normalized to lowercase. The map preserves insertion order.
     *
     * @return an unmodifiable map of parameters
     */
    public Map<String, String> parameters() {
        return parameters;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type).append('/').append(subtype);

        for (Map.Entry<String, String> e : parameters.entrySet()) {
            sb.append("; ").append(e.getKey()).append('=').append(quoteIfNeeded(e.getValue()));
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaType other)) return false;
        return type.equals(other.type)
                && subtype.equals(other.subtype)
                && parameters.equals(other.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, subtype, parameters);
    }

    private static String normalizeToken(String token) {
        String t = Objects.requireNonNull(token, "token").trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Invalid token: empty");
        return t.toLowerCase(Locale.ROOT);
    }

    private static String stripQuotes(String v) {
        if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String quoteIfNeeded(String v) {
        // Quote if spaces/semicolons/equals are present (simple rule; enough for HTTP headers)
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c <= ' ' || c == ';' || c == '=' || c == '"') {
                return "\"" + v.replace("\"", "\\\"") + "\"";
            }
        }
        return v;
    }

    /**
     * Checks if this media type matches the specified media type.
     * <p>
     * Matching rules:
     * <ul>
     * <li>Wildcards ("&#42;") in the other media type match any value</li>
     * <li>Type and subtype must match (unless wildcarded)</li>
     * <li>All parameters in the other media type must be present in this media type with the same values</li>
     * <li>This media type may have additional parameters not present in the other</li>
     * </ul>
     * <p>
     * Examples:
     * <ul>
     * <li>"application/json" matches "application/json"</li>
     * <li>"application/json" matches "&#42;/&#42;"</li>
     * <li>"text/plain; charset=utf-8" matches "text/plain"</li>
     * <li>"text/plain" does NOT match "text/plain; charset=utf-8"</li>
     * </ul>
     *
     * @param other the media type to match against
     * @return true if this media type matches the other, false otherwise
     * @throws NullPointerException if other is null
     */
    public boolean is(MediaType other) {
        Objects.requireNonNull(other, "other");

        // type match
        if (!"*".equals(other.type) && !this.type.equals(other.type)) {
            return false;
        }

        // subtype match
        if (!"*".equals(other.subtype) && !this.subtype.equals(other.subtype)) {
            return false;
        }

        // parameter match: other ⊆ this
        for (Map.Entry<String, String> e : other.parameters.entrySet()) {
            String value = this.parameters.get(e.getKey());
            if (!e.getValue().equals(value)) {
                return false;
            }
        }

        return true;
    }

    // --- handy common constants for HTTP clients ---
    public static final MediaType ANY_TYPE = MediaType.create("*", "*");
    public static final MediaType ANY_TEXT_TYPE = MediaType.create("text", "*");
    public static final MediaType ANY_APPLICATION_TYPE = MediaType.create("application", "*");

    public static final MediaType JSON_UTF_8 = MediaType.create("application", "json").withCharset(java.nio.charset.StandardCharsets.UTF_8);
    public static final MediaType PLAIN_TEXT_UTF_8 = MediaType.create("text", "plain").withCharset(java.nio.charset.StandardCharsets.UTF_8);
}
