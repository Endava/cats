package com.endava.cats.command.triage;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.util.KeyValuePair;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replays the representative request of a cluster ({@code --verify}) and decides {@code FIXED}, {@code STILL_FAILING}
 * or {@code INCONCLUSIVE}. A replay is inconclusive when it cannot prove anything about the original failure: masked
 * {@code $$ENV} credentials are missing, the request could not be sent, an unrelated {@code 401}/{@code 403} or
 * {@code 404} (stale resource IDs from the original run) is returned, or the cluster is contract-only drift.
 */
final class LiveClusterVerifier {
    static final String FIXED = "FIXED";
    static final String STILL_FAILING = "STILL_FAILING";
    static final String INCONCLUSIVE = "INCONCLUSIVE";
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\$(\\w+)");
    private static final String DEFAULT_CONTENT_TYPE = "application/json";

    /**
     * Outcome of a live replay; all components are {@code null} when verification is disabled.
     */
    record Verification(String status, Integer statusCode, String note) {
        static Verification disabled() {
            return new Verification(null, null, null);
        }

        static Verification inconclusive(Integer statusCode, String note) {
            return new Verification(INCONCLUSIVE, statusCode, note);
        }
    }

    private record ResolvedText(String value, Set<String> missingVariables) {
    }

    private final OkHttpClient client;
    private final String serverOverride;
    private final Map<String, String> headerOverrides;
    private final UnaryOperator<String> environment;

    LiveClusterVerifier(String serverOverride, Map<String, String> headerOverrides, UnaryOperator<String> environment) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .followRedirects(false)
                .build();
        this.serverOverride = serverOverride;
        this.headerOverrides = Map.copyOf(Optional.ofNullable(headerOverrides).orElse(Map.of()));
        this.environment = environment;
    }

    Verification verify(ClusterClassification classification, TriageTestCase representative) {
        if (!classification.liveVerifiable()) {
            return Verification.inconclusive(null, "Contract drift cannot be proven by a live replay; re-run CATS against the updated contract.");
        }
        CatsRequest request = representative.testCase().getRequest();
        ResolvedText url = resolveEnv(StringUtils.defaultIfBlank(request.getUrl(), representative.testCase().getFullRequestPath()));
        Map<String, ResolvedText> headers = resolvedHeaders(request);
        Set<String> missing = missingVariables(url, headers);
        if (!missing.isEmpty()) {
            return Verification.inconclusive(null, "Missing environment variable(s) %s required by masked report values; export them or pass -H."
                    .formatted(String.join(", ", missing)));
        }
        int code = execute(request, resolveTargetUrl(url.value()), headers);
        return judge(classification, representative, code);
    }

    private Verification judge(ClusterClassification classification, TriageTestCase representative, int code) {
        if (code == 0) {
            return Verification.inconclusive(null, "The request could not be sent (connection or I/O error).");
        }
        boolean authCluster = "AUTH_CONFIGURATION_GAP".equals(classification.category());
        if ((code == 401 || code == 403) && !authCluster) {
            return Verification.inconclusive(code, "Replay was rejected by authentication; export the masked credentials or pass -H.");
        }
        if (code == 404 && representative.statusCode() != 404) {
            return Verification.inconclusive(code, "Resource IDs from the original run no longer exist; use `cats replay %s --stateful`."
                    .formatted(ClusterClassifier.testId(representative)));
        }
        return new Verification(isFixed(classification, representative.expected(), code) ? FIXED : STILL_FAILING, code, null);
    }

    static boolean isFixed(ClusterClassification classification, ExpectedCodes expected, int code) {
        if ("SERVER_CRASH_5XX".equals(classification.category())) {
            return code < 500;
        }
        return expected.isEmpty() ? ResponseCodeFamily.is2xxCode(code) : expected.allows(code);
    }

    private Map<String, ResolvedText> resolvedHeaders(CatsRequest request) {
        Map<String, ResolvedText> resolved = new LinkedHashMap<>();
        Optional.ofNullable(request.getHeaders()).orElse(List.of()).stream()
                .filter(header -> StringUtils.isNotBlank(header.getKey()) && header.getValue() != null)
                .filter(header -> !isOverridden(header))
                .forEach(header -> resolved.put(header.getKey(), resolveEnv(String.valueOf(header.getValue()))));
        headerOverrides.forEach((name, value) -> resolved.put(name, new ResolvedText(value, Set.of())));
        return resolved;
    }

    private boolean isOverridden(KeyValuePair<String, Object> header) {
        return headerOverrides.keySet().stream().anyMatch(name -> name.equalsIgnoreCase(header.getKey()));
    }

    private static Set<String> missingVariables(ResolvedText url, Map<String, ResolvedText> headers) {
        Set<String> missing = new LinkedHashSet<>(url.missingVariables());
        headers.values().forEach(header -> missing.addAll(header.missingVariables()));
        return missing;
    }

    /**
     * Expands every embedded {@code $$VAR} placeholder (reports mask secrets as {@code $$<HeaderName>}).
     */
    private ResolvedText resolveEnv(String text) {
        Set<String> missing = new LinkedHashSet<>();
        Matcher matcher = ENV_PLACEHOLDER.matcher(StringUtils.defaultString(text));
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = environment.apply(matcher.group(1));
            if (value == null) {
                missing.add(matcher.group(1));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(Optional.ofNullable(value).orElse(matcher.group())));
        }
        matcher.appendTail(result);
        return new ResolvedText(result.toString(), Set.copyOf(missing));
    }

    private int execute(CatsRequest request, String url, Map<String, ResolvedText> headers) {
        try {
            Headers.Builder headersBuilder = new Headers.Builder();
            headers.forEach((name, value) -> headersBuilder.set(name, value.value()));
            String method = StringUtils.defaultIfBlank(request.getHttpMethod(), "GET").toUpperCase(Locale.ROOT);
            Request httpRequest = new Request.Builder().url(url).headers(headersBuilder.build())
                    .method(method, body(request, method, contentType(headers))).build();
            try (Response response = client.newCall(httpRequest).execute()) {
                return response.code();
            }
        } catch (IOException | RuntimeException _) {
            return 0;
        }
    }

    private static String contentType(Map<String, ResolvedText> headers) {
        return headers.entrySet().stream()
                .filter(entry -> "content-type".equalsIgnoreCase(entry.getKey()))
                .map(entry -> entry.getValue().value())
                .findFirst()
                .orElse(DEFAULT_CONTENT_TYPE);
    }

    private static RequestBody body(CatsRequest request, String method, String contentType) {
        boolean bodyAllowed = !"GET".equals(method) && !"HEAD".equals(method);
        if (!HttpMethod.requiresBody(method) && !(request.isSendBody() && bodyAllowed)) {
            return null;
        }
        MediaType mediaType = Optional.ofNullable(MediaType.parse(contentType)).orElse(MediaType.get(DEFAULT_CONTENT_TYPE));
        return RequestBody.create(StringUtils.defaultString(request.getPayload()).getBytes(StandardCharsets.UTF_8), mediaType);
    }

    /**
     * Replaces scheme/host/port with {@code -s} and prefixes its base path unless the original path already contains it.
     */
    String resolveTargetUrl(String originalUrl) {
        HttpUrl original = HttpUrl.parse(StringUtils.defaultString(originalUrl));
        HttpUrl override = HttpUrl.parse(StringUtils.defaultString(serverOverride));
        if (original == null || override == null) {
            return originalUrl;
        }
        String basePath = Strings.CS.removeEnd(override.encodedPath(), "/");
        String path = basePath.isEmpty() || original.encodedPath().startsWith(basePath)
                ? original.encodedPath()
                : basePath + original.encodedPath();
        return original.newBuilder().scheme(override.scheme()).host(override.host()).port(override.port()).encodedPath(path).build().toString();
    }
}
