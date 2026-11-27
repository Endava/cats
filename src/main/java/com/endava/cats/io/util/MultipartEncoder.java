package com.endava.cats.io.util;

import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Encodes a JSON payload as a {@code multipart/form-data} request body.
 * <p>
 * Every top-level or flattened property becomes a part. Properties whose value starts with {@code cats_file:} or
 * whose schema is a binary string are sent as file parts; everything else is sent as a text part.
 */
public final class MultipartEncoder {
    private static final String FILE_PREFIX = CatsDSLWords.CATS_FILE_PREFIX;
    private static final String BOUNDARY_PREFIX = "cats";
    private static final String OCTET_STREAM = "application/octet-stream";

    private MultipartEncoder() {
        //ntd
    }

    /**
     * Encodes the payload as a multipart body. Payloads that cannot be represented as named parts (blank, invalid
     * JSON, non-objects and empty objects) are sent unchanged.
     *
     * @param payload        the JSON payload
     * @param validJson      whether the payload is valid JSON
     * @param propertyTypes  the request property schemas, keyed by property name
     * @param isFuzzedField  tells if a property is currently being fuzzed and must be sent as generated
     * @return the encoded body, its content type including the boundary and a printable summary
     */
    public static Result encode(String payload, boolean validJson, Map<String, Schema> propertyTypes,
                                Predicate<String> isFuzzedField) {
        String boundary = BOUNDARY_PREFIX + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> fields = parseObject(payload, validJson);
        if (fields.isEmpty()) {
            return new Result(payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8),
                    contentType(boundary), StringUtils.defaultString(payload), boundary, List.of());
        }

        Map<String, Object> summary = new LinkedHashMap<>(fields);
        List<Part> parts = new ArrayList<>();
        for (KeyValuePair<String, Object> field : FormEncoder.flattenParams(fields)) {
            String name = field.getKey();
            Object value = field.getValue();
            if (isFilePart(name, value, propertyTypes)) {
                Part file = toFilePart(name, value, isFuzzedField.test(name));
                parts.add(file);
                summary.put(name, describe(file));
            } else {
                parts.add(new Part(name, null, null, String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
            }
        }
        return new Result(render(boundary, parts), contentType(boundary),
                JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(summary), boundary, List.copyOf(parts));
    }

    /**
     * Builds the {@code Content-Type} header value for a multipart body.
     *
     * @param boundary the multipart boundary
     * @return the header value including the boundary
     */
    public static String contentType(String boundary) {
        return "multipart/form-data; boundary=" + boundary;
    }

    /**
     * Renders the parts as a {@code multipart/form-data} body. Quotes and line breaks in part and file names are
     * percent-encoded.
     *
     * @param boundary the multipart boundary
     * @param parts    the parts, in the order they are written
     * @return the exact bytes of the body
     */
    public static byte[] render(String boundary, List<Part> parts) {
        return render(boundary, parts, Map.of());
    }

    /**
     * Renders the parts as a {@code multipart/form-data} body, replacing the default header lines of the parts found
     * in {@code headerOverrides}. Overrides are written as they are, without any escaping.
     *
     * @param boundary        the multipart boundary
     * @param parts           the parts, in the order they are written
     * @param headerOverrides header lines by part index; a non-empty value must end with a CRLF
     * @return the exact bytes of the body
     */
    public static byte[] render(String boundary, List<Part> parts, Map<Integer, String> headerOverrides) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int index = 0; index < parts.size(); index++) {
            Part part = parts.get(index);
            StringBuilder header = new StringBuilder("--").append(boundary).append("\r\n");
            if (headerOverrides.containsKey(index)) {
                header.append(headerOverrides.get(index));
            } else {
                header.append("Content-Disposition: form-data; name=\"").append(escape(part.name())).append('"');
                if (part.isFile()) {
                    header.append("; filename=\"").append(escape(part.fileName())).append('"');
                }
                header.append("\r\n");
                if (part.isFile()) {
                    header.append("Content-Type: ").append(part.contentType()).append("\r\n");
                }
            }
            header.append("\r\n");
            out.writeBytes(header.toString().getBytes(StandardCharsets.UTF_8));
            out.writeBytes(part.bytes());
            out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String escape(String value) {
        return value.replace("\"", "%22").replace("\r", "%0D").replace("\n", "%0A");
    }

    private static Map<String, Object> parseObject(String payload, boolean validJson) {
        if (!validJson || StringUtils.isBlank(payload)) {
            return Map.of();
        }
        try {
            Object parsed = JsonUtils.getSimpleObjectMapper().readValue(payload, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> fields = new LinkedHashMap<>();
                map.forEach((key, value) -> fields.put(String.valueOf(key), value));
                removeNulls(fields);
                return fields;
            }
        } catch (IOException _) {
            return Map.of();
        }
        return Map.of();
    }

    private static void removeNulls(Map<String, Object> map) {
        Iterator<Map.Entry<String, Object>> entries = map.entrySet().iterator();
        while (entries.hasNext()) {
            Object value = entries.next().getValue();
            if (value == null) {
                entries.remove();
            } else if (value instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) value;
                removeNulls(nested);
            }
        }
    }

    private static boolean isFilePart(String name, Object value, Map<String, Schema> propertyTypes) {
        if (value instanceof String text && text.startsWith(FILE_PREFIX)) {
            return true;
        }
        return !name.contains("[") && propertyTypes != null && CatsModelUtils.isBinarySchema(propertyTypes.get(name));
    }

    private static Part toFilePart(String name, Object value, boolean fuzzed) {
        String text = String.valueOf(value);
        if (text.startsWith(FILE_PREFIX)) {
            return readReferencedFile(name, text.substring(FILE_PREFIX.length()));
        }
        byte[] bytes = fuzzed ? text.getBytes(StandardCharsets.UTF_8) : decodeBase64OrUtf8(text);
        return new Part(name, name + ".bin", OCTET_STREAM, bytes);
    }

    private static Part readReferencedFile(String name, String location) {
        Path path = Path.of(location);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("File referenced by " + FILE_PREFIX + " does not exist: " + location);
        }
        String fileName = String.valueOf(path.getFileName());
        try {
            return new Part(name, fileName, detectContentType(path, fileName), Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read file referenced by " + FILE_PREFIX + ": " + location, e);
        }
    }

    private static String detectContentType(Path path, String fileName) {
        String detected = null;
        try {
            detected = Files.probeContentType(path);
        } catch (IOException _) {
            //fall back to the file name
        }
        if (detected == null) {
            detected = URLConnection.guessContentTypeFromName(fileName);
        }
        return detected == null ? OCTET_STREAM : detected;
    }

    private static byte[] decodeBase64OrUtf8(String text) {
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException _) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String describe(Part file) {
        return "<file name=" + file.fileName() + " size=" + file.bytes().length + " sha256=" + sha256(file.bytes()) + ">";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A single part of a multipart body.
     *
     * @param name        the form field name
     * @param fileName    the file name, or null for a text part
     * @param contentType the part {@code Content-Type}, or null for a text part
     * @param bytes       the part content
     */
    public record Part(String name, String fileName, String contentType, byte[] bytes) {
        public boolean isFile() {
            return fileName != null;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Part(String otherName, String otherFileName, String otherContentType, byte[] otherBytes)
                    && Objects.equals(name, otherName)
                    && Objects.equals(fileName, otherFileName)
                    && Objects.equals(contentType, otherContentType)
                    && Arrays.equals(bytes, otherBytes);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(name, fileName, contentType) + Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "Part[name=" + name + ", fileName=" + fileName + ", contentType=" + contentType
                    + ", bytes=" + Arrays.toString(bytes) + "]";
        }
    }

    /**
     * The encoded request body.
     *
     * @param body        the exact bytes to send
     * @param contentType the {@code Content-Type} header value, including the boundary
     * @param summary     a printable description of the body with file contents replaced by name, size and checksum
     * @param boundary    the multipart boundary used in {@code body}
     * @param parts       the parts the body was rendered from; empty when the payload was sent as is
     */
    public record Result(byte[] body, String contentType, String summary, String boundary, List<Part> parts) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Result(byte[] otherBody, String otherContentType, String otherSummary, String otherBoundary, List<Part> otherParts)
                    && Arrays.equals(body, otherBody)
                    && Objects.equals(contentType, otherContentType)
                    && Objects.equals(summary, otherSummary)
                    && Objects.equals(boundary, otherBoundary)
                    && Objects.equals(parts, otherParts);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(contentType, summary, boundary, parts) + Arrays.hashCode(body);
        }

        @Override
        public String toString() {
            return "Result[body=" + Arrays.toString(body) + ", contentType=" + contentType
                    + ", summary=" + summary + ", boundary=" + boundary + ", parts=" + parts + "]";
        }
    }
}
