package com.endava.cats.util;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsTestCase;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import okhttp3.HttpUrl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SensitiveDataPolicy {
    public static final String REDACTED = "[REDACTED]";
    private static final Set<String> SENSITIVE_HEADER_PARTS = Set.of(
            "authorization", "authorisation", "cookie", "token", "jwt", "apikey", "secret", "password", "credential", "appkey", "appid");
    private static final Set<String> SENSITIVE_QUERY_NAMES = Set.of(
            "accesstoken", "idtoken", "authtoken", "apikey", "jwt", "password", "secret", "signature", "sig", "token");

    private SensitiveDataPolicy() {
    }

    public static boolean isSensitiveHeader(String header, Set<String> configuredHeaders, boolean showSecrets) {
        if (header == null) {
            return false;
        }
        boolean explicitlyConfigured = Optional.ofNullable(configuredHeaders).orElseGet(Set::of).stream()
                .anyMatch(configured -> configured.equalsIgnoreCase(header));
        if (explicitlyConfigured) {
            return true;
        }
        String normalized = normalize(header);
        return !showSecrets && SENSITIVE_HEADER_PARTS.stream().anyMatch(normalized::contains);
    }

    public static boolean isSensitiveQueryParam(String queryParam, Set<String> configuredQueryParams, boolean showSecrets) {
        if (queryParam == null) {
            return false;
        }
        boolean explicitlyConfigured = Optional.ofNullable(configuredQueryParams).orElseGet(Set::of).stream()
                .anyMatch(configured -> configured.equalsIgnoreCase(queryParam));
        String normalized = normalize(queryParam);
        return explicitlyConfigured || (!showSecrets && SENSITIVE_QUERY_NAMES.contains(normalized));
    }

    public static String headerEnvironmentVariable(String header) {
        return header.replaceAll("[^A-Za-z0-9]", "");
    }

    public static String queryEnvironmentVariable(String queryParam) {
        String variable = queryParam.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase(Locale.ROOT);
        return variable.isEmpty() || Character.isDigit(variable.charAt(0)) ? "_" + variable : variable;
    }

    public static Set<String> openApiQueryApiKeyNames(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getComponents() == null || openAPI.getComponents().getSecuritySchemes() == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        openAPI.getComponents().getSecuritySchemes().values().stream()
                .filter(scheme -> SecurityScheme.Type.APIKEY.equals(scheme.getType()))
                .filter(scheme -> SecurityScheme.In.QUERY.equals(scheme.getIn()))
                .map(SecurityScheme::getName)
                .filter(name -> name != null && !name.isBlank())
                .forEach(result::add);
        return Set.copyOf(result);
    }

    public static String placeholder(String environmentVariable) {
        return "$$" + environmentVariable;
    }

    public static List<KeyValuePair<String, Object>> maskHeadersForDisplay(List<? extends KeyValuePair<String, ?>> headers,
                                                                           ReportingArguments reportingArguments) {
        List<KeyValuePair<String, Object>> result = new ArrayList<>();
        for (KeyValuePair<String, ?> header : Optional.ofNullable(headers).orElseGet(List::of)) {
            Object value = reportingArguments.shouldMaskHeader(header.getKey())
                    ? placeholder(headerEnvironmentVariable(header.getKey())) : header.getValue();
            result.add(new KeyValuePair<>(header.getKey(), value));
        }
        return result;
    }

    public static Map<String, Object> maskHeadersForDisplay(Map<String, ?> headers, ReportingArguments reportingArguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        Optional.ofNullable(headers).orElseGet(Map::of).forEach((name, value) -> result.put(name,
                reportingArguments.shouldMaskHeader(name) ? placeholder(headerEnvironmentVariable(name)) : value));
        return result;
    }

    public static void sanitize(CatsTestCase testCase, ReportingArguments reportingArguments) {
        CatsRequest request = testCase.getRequest();
        if (request != null) {
            for (KeyValuePair<String, Object> header : Optional.ofNullable(request.getHeaders()).orElseGet(List::of)) {
                if (reportingArguments.shouldMaskHeader(header.getKey())) {
                    String variable = headerEnvironmentVariable(header.getKey());
                    reportingArguments.registerSensitiveHeaders(Set.of(header.getKey()));
                    reportingArguments.registerReplayEnvironmentVariable(variable);
                    header.setValue(placeholder(variable));
                }
            }
            request.setUrl(maskUrl(request.getUrl(), reportingArguments));
            testCase.setFullRequestPath(maskUrl(testCase.getFullRequestPath(), reportingArguments));
            testCase.setPath(maskUrl(testCase.getPath(), reportingArguments));
            testCase.setContractPath(maskUrl(testCase.getContractPath(), reportingArguments));
        }
        if (testCase.getResponse() != null) {
            for (KeyValuePair<String, String> header : Optional.ofNullable(testCase.getResponse().getHeaders()).orElseGet(List::of)) {
                if (reportingArguments.shouldMaskHeader(header.getKey())) {
                    reportingArguments.registerSensitiveHeaders(Set.of(header.getKey()));
                    header.setValue(REDACTED);
                }
            }
        }
    }

    public static String maskUrl(String url, ReportingArguments reportingArguments) {
        if (url == null || !url.startsWith("http")) {
            return url;
        }
        try {
            HttpUrl parsed = HttpUrl.get(url);
            HttpUrl.Builder builder = parsed.newBuilder();
            for (String queryName : new LinkedHashSet<>(parsed.queryParameterNames())) {
                if (reportingArguments.shouldMaskQueryParam(queryName)) {
                    String variable = queryEnvironmentVariable(queryName);
                    reportingArguments.registerReplayEnvironmentVariable(variable);
                    int valueCount = parsed.queryParameterValues(queryName).size();
                    builder.removeAllQueryParameters(queryName);
                    for (int i = 0; i < valueCount; i++) {
                        builder.addQueryParameter(queryName, placeholder(variable));
                    }
                }
            }
            return builder.build().toString().replace("%24%24", "$$");
        } catch (IllegalArgumentException _) {
            return url;
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
