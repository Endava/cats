package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.model.CatsResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Removes echoes of an injected payload from a response body, so that evidence detection only looks at
 * content the server produced rather than input it merely reflected (e.g. inside validation messages or
 * created-resource representations). This mirrors the reflected-value stripping done by ZAP scan rules.
 */
public final class PayloadReflections {

    private PayloadReflections() {
    }

    /**
     * Returns a copy of the response whose body no longer contains the payload, in its raw form or in any of
     * the common JSON, HTML, XML or URL encodings, matched case-insensitively.
     *
     * @param response the response received for the injected payload
     * @param payload  the injected payload
     * @return the response itself when nothing was reflected, otherwise a copy with a stripped body
     */
    public static CatsResponse strip(CatsResponse response, String payload) {
        if (StringUtils.isAnyEmpty(response.getBody(), payload)) {
            return response;
        }
        String stripped = encodedVariants(payload).stream()
                .reduce(response.getBody(), PayloadReflections::removeIgnoringCase, (first, _) -> first);
        return stripped.equals(response.getBody()) ? response : withBody(response, stripped);
    }

    static List<String> encodedVariants(String payload) {
        String jsonEscaped = StringEscapeUtils.escapeJson(payload);
        String jsonEscapedKeepingSlashes = jsonEscaped.replace("\\/", "/");
        String urlEncoded = URLEncoder.encode(payload, StandardCharsets.UTF_8);
        return Stream.of(payload, jsonEscaped, jsonEscapedKeepingSlashes,
                        htmlSafeJsonEscaped(jsonEscaped), htmlSafeJsonEscaped(jsonEscapedKeepingSlashes),
                        StringEscapeUtils.escapeHtml4(payload), StringEscapeUtils.escapeXml10(payload),
                        urlEncoded, urlEncoded.replace("+", "%20"))
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private static String htmlSafeJsonEscaped(String jsonEscaped) {
        return jsonEscaped.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
                .replace("=", "\\u003d").replace("'", "\\u0027");
    }

    private static String removeIgnoringCase(String body, String variant) {
        return Pattern.compile(Pattern.quote(variant), Pattern.CASE_INSENSITIVE).matcher(body).replaceAll("");
    }

    private static CatsResponse withBody(CatsResponse response, String body) {
        return CatsResponse.builder()
                .responseCode(response.getResponseCode())
                .httpMethod(response.getHttpMethod())
                .responseTimeInMs(response.getResponseTimeInMs())
                .headers(response.getHeaders())
                .responseContentType(response.getResponseContentType())
                .path(response.getPath())
                .body(body)
                .build();
    }
}
