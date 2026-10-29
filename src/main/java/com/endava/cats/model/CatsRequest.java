package com.endava.cats.model;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.util.KeyValuePair;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Model class used to stored request details.
 */
@Getter
@Setter
@Builder
public class CatsRequest {
    List<KeyValuePair<String, Object>> headers;
    String payload;
    String httpMethod;
    String url;
    boolean sendBody;
    boolean preserveContentType;

    @Builder.Default
    String timestamp = DateTimeFormatter.RFC_1123_DATE_TIME.format(OffsetDateTime.now(ZoneId.of("UTC")));

    /**
     * Creates an empty CatsRequest with placeholder values.
     * The generated request has an empty JSON payload, an undefined HTTP method ("####"),
     * an undefined URL ("####"), and an empty list of headers.
     *
     * @return An empty CatsRequest instance.
     */
    public static CatsRequest empty() {
        CatsRequest request = CatsRequest.builder().build();
        request.payload = "{}";
        request.httpMethod = "####";
        request.url = "####";
        request.headers = Collections.emptyList();
        return request;
    }

    /**
     * Formats this request as a shell-escaped single-line {@code curl} command.
     */
    public String toCurlCommand() {
        if (httpMethod == null || "####".equals(httpMethod)) {
            return "";
        }
        String method = Optional.ofNullable(httpMethod).orElse("GET");
        StringBuilder cmd = new StringBuilder("curl -i -X ").append(method);
        for (KeyValuePair<String, Object> header : Optional.ofNullable(headers).orElse(Collections.emptyList())) {
            if (StringUtils.isNotBlank(header.getKey())) {
                cmd.append(" -H ").append(shellQuote(header.getKey() + ": " + header.getValue()));
            }
        }
        if (StringUtils.isNotBlank(payload) && (HttpMethod.requiresBody(method) || sendBody)) {
            cmd.append(" -d ").append(shellQuote(payload));
        }
        cmd.append(" ").append(shellQuote(Optional.ofNullable(url).orElse("")));
        return cmd.toString();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
