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

    public static MediaType create(String type, String subtype) {
        return new MediaType(type, subtype, Map.of());
    }

    /**
     * Parses strings like:
     * - "application/json"
     * - "application/json; charset=UTF-8"
     * - "text/plain; charset=\"utf-8\"; format=flowed"
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
            if (p.isEmpty()) continue;

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

    public String type() {
        return type;
    }

    public String subtype() {
        return subtype;
    }

    public boolean hasWildcard() {
        return "*".equals(type) || "*".equals(subtype);
    }

    public Optional<Charset> charset() {
        String cs = parameters.get("charset");
        if (cs == null || cs.isBlank()) return Optional.empty();
        try {
            return Optional.of(Charset.forName(cs));
        } catch (Exception e) {
            // unknown/invalid charset => behave safely
            return Optional.empty();
        }
    }

    public MediaType withCharset(Charset charset) {
        Objects.requireNonNull(charset, "charset");
        return withParameter("charset", charset.name().toLowerCase(Locale.ROOT));
    }

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

    public MediaType withoutParameters() {
        if (parameters.isEmpty()) return this;
        return new MediaType(type, subtype, Map.of());
    }

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

    // ---------- helpers ----------

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
