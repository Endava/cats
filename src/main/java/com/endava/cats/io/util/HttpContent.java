package com.endava.cats.io.util;


import com.endava.cats.util.KeyValuePair;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Represents the content of an HTTP request, i.e. the request's body. This class also holds the
 * value of the {@code Content-Type} header, which can depend on the body in some cases (e.g. for
 * multipart requests).
 */
@Getter
@ToString
@EqualsAndHashCode
public final class HttpContent {
    /**
     * The request's content, as a byte array.
     */
    private final byte[] byteArrayContent;
    /**
     * The value of the {@code Content-Type} header.
     */
    private final String contentType;

    private HttpContent(byte[] byteArrayContent, String contentType) {
        this.byteArrayContent = byteArrayContent;
        this.contentType = contentType;
    }

    /**
     * Builds a new HttpContent for name/value tuples encoded using {@code
     * application/x-www-form-urlencoded} MIME type.
     *
     * @param nameValueCollection the collection of name/value tuples to encode
     * @return the encoded HttpContent instance
     * @throws IllegalArgumentException if nameValueCollection is null
     */
    public static HttpContent buildFormURLEncodedContent(Collection<KeyValuePair<String, String>> nameValueCollection) {
        requireNonNull(nameValueCollection);
        return new HttpContent(FormEncoder.createQueryString(nameValueCollection).getBytes(StandardCharsets.UTF_8),
                String.format("application/x-www-form-urlencoded;charset=%s", StandardCharsets.UTF_8));
    }

    /**
     * The request's content, as a string.
     *
     * @return the content as string
     */
    public String stringContent() {
        return new String(this.byteArrayContent, StandardCharsets.UTF_8);
    }

    /**
     * Builds a new HttpContent for name/value tuples encoded using {@code multipart/form-data} MIME
     * type.
     *
     * @param nameValueCollection the collection of name/value tuples to encode
     * @return the encoded HttpContent instance
     * @throws IllegalArgumentException if nameValueCollection is null
     * @throws IOException              if some IO exception happens
     */
    public static HttpContent buildMultipartFormDataContent(Collection<KeyValuePair<String, Object>> nameValueCollection) throws IOException {
        String boundary = UUID.randomUUID().toString();
        return buildMultipartFormDataContent(nameValueCollection, boundary);
    }

    /**
     * Builds a new HttpContent for name/value tuples encoded using {@code multipart/form-data} MIME
     * type.
     *
     * @param nameValueCollection the collection of name/value tuples to encode
     * @param boundary            the boundary
     * @return the encoded HttpContent instance
     * @throws IllegalArgumentException if nameValueCollection is null
     * @throws IOException              if some IO exception happens
     */
    public static HttpContent buildMultipartFormDataContent(Collection<KeyValuePair<String, Object>> nameValueCollection, String boundary) throws IOException {
        requireNonNull(nameValueCollection);
        MultipartProcessor multipartProcessor = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            multipartProcessor = new MultipartProcessor(baos, boundary, StandardCharsets.UTF_8);
            for (KeyValuePair<String, Object> entry : nameValueCollection) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof File file) {
                    multipartProcessor.addFileField(key, file.getName(), new FileInputStream(file));
                } else if (value instanceof InputStream inputStream) {
                    multipartProcessor.addFileField(key, "blob", inputStream);
                } else {
                    multipartProcessor.addFormField(key, (String) value);
                }
            }
            return new HttpContent(baos.toByteArray(), String.format("multipart/form-data; boundary=%s", boundary));
        } finally {
            if (multipartProcessor != null) {
                multipartProcessor.finish();
            }
        }
    }
}
