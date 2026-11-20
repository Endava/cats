package com.endava.cats.io.util;

import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import io.swagger.v3.oas.models.media.Schema;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.Buffer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Encodes a JSON payload as a {@code multipart/form-data} request body.
 * <p>
 * Every top-level or flattened property becomes a part. Properties whose value starts with {@code cats_file:} or
 * whose schema is a binary string are sent as file parts; everything else is sent as a text part.
 */
public final class MultipartEncoder {
    private static final String FILE_PREFIX = "cats_file:";
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
        if (fields == null || fields.isEmpty()) {
            return new Result(payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8),
                    "multipart/form-data; boundary=" + boundary, StringUtils.defaultString(payload));
        }

        Map<String, Object> summary = new LinkedHashMap<>(fields);
        MultipartBody.Builder builder = new MultipartBody.Builder(boundary).setType(MultipartBody.FORM);
        int partCount = 0;
        for (KeyValuePair<String, Object> field : FormEncoder.flattenParams(fields)) {
            String name = field.getKey();
            Object value = field.getValue();
            if (isFilePart(name, value, propertyTypes)) {
                FilePart file = toFilePart(name, value, isFuzzedField.test(name));
                builder.addPart(MultipartBody.Part.createFormData(name, file.fileName(),
                        RequestBody.create(file.bytes(), MediaType.parse(file.contentType()))));
                summary.put(name, file.describe());
            } else {
                builder.addPart(MultipartBody.Part.createFormData(name, String.valueOf(value)));
            }
            partCount++;
        }

        if (partCount == 0) {
            return new Result(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8),
                    "multipart/form-data; boundary=" + boundary, JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(summary));
        }
        MultipartBody body = builder.build();
        return new Result(toBytes(body), String.valueOf(body.contentType()),
                JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(summary));
    }

    private static Map<String, Object> parseObject(String payload, boolean validJson) {
        if (!validJson || StringUtils.isBlank(payload)) {
            return null;
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
            return null;
        }
        return null;
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

    private static FilePart toFilePart(String name, Object value, boolean fuzzed) {
        String text = String.valueOf(value);
        if (text.startsWith(FILE_PREFIX)) {
            return readReferencedFile(text.substring(FILE_PREFIX.length()));
        }
        byte[] bytes = fuzzed ? text.getBytes(StandardCharsets.UTF_8) : decodeBase64OrUtf8(text);
        return new FilePart(name + ".bin", OCTET_STREAM, bytes);
    }

    private static FilePart readReferencedFile(String location) {
        Path path = Path.of(location);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("File referenced by " + FILE_PREFIX + " does not exist: " + location);
        }
        String fileName = String.valueOf(path.getFileName());
        try {
            return new FilePart(fileName, detectContentType(path, fileName), Files.readAllBytes(path));
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

    private static byte[] toBytes(MultipartBody body) {
        try (Buffer buffer = new Buffer()) {
            body.writeTo(buffer);
            return buffer.readByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to encode multipart body", e);
        }
    }

    private record FilePart(String fileName, String contentType, byte[] bytes) {
        String describe() {
            return "<file name=" + fileName + " size=" + bytes.length + " sha256=" + sha256() + ">";
        }

        private String sha256() {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * The encoded request body.
     *
     * @param body        the exact bytes to send
     * @param contentType the {@code Content-Type} header value, including the boundary
     * @param summary     a printable description of the body with file contents replaced by name, size and checksum
     */
    public record Result(byte[] body, String contentType, String summary) {
    }
}
