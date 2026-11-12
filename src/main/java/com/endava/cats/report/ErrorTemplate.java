package com.endava.cats.report;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A mined error template, e.g. {@code Bad Request: <FIELD> must not be blank}.
 *
 * @param id   8 hex characters of the SHA-256 of the status code and template text, stable for the same template
 * @param text the human-readable template
 */
public record ErrorTemplate(String id, String text) {
    static final String EMPTY_BODY = "[Empty response body]";

    static ErrorTemplate of(ErrorSample sample, List<String> templateTokens) {
        String text = render(sample.signature(), templateTokens);
        return new ErrorTemplate(shortHash(sample.statusCode() + "|" + text), text);
    }

    private static String render(ErrorSignature signature, List<String> templateTokens) {
        String detail = String.join(" ", templateTokens);
        String message = ErrorSignatureMasker.EMPTY_TYPE.equals(signature.errorType())
                ? renderEmptyBody(detail)
                : Stream.of(signature.errorType(), detail).filter(StringUtils::isNotBlank).collect(Collectors.joining(": "));
        return StringUtils.isBlank(signature.stackFrame()) ? message : message + " @ " + signature.stackFrame();
    }

    private static String renderEmptyBody(String detail) {
        return detail.isEmpty() ? EMPTY_BODY : "%s (%s)".formatted(EMPTY_BODY, detail);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
