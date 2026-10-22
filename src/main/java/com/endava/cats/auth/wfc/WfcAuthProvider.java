package com.endava.cats.auth.wfc;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.exception.CatsException;
import com.endava.cats.io.BoundedResponseBodyReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webfuzzing.commons.auth.Auth;
import com.webfuzzing.commons.auth.AuthenticationInfo;
import com.webfuzzing.commons.auth.Header;
import com.webfuzzing.commons.auth.LoginEndpoint;
import com.webfuzzing.commons.auth.PayloadUsernamePassword;
import com.webfuzzing.commons.auth.TokenHandling;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves WFC Auth credentials into request headers and query parameters.
 */
@DryRun
@ApplicationScoped
public class WfcAuthProvider {
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String COOKIE = "Cookie";
    private static final String LOCATION = "Location";
    private static final String SET_COOKIE = "Set-Cookie";
    private static final String TOKEN_PLACEHOLDER = "{token}";
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(WfcAuthProvider.class);
    private final WfcAuthConfigLoader loader = new WfcAuthConfigLoader();
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final AuthArguments authArguments;
    private final ApiArguments apiArguments;
    private File loadedFile;
    private String loadedAuthName;
    private AuthenticationInfo selectedAuth;
    private List<AuthenticationInfo> authEntries = List.of();
    private final Map<String, WfcCredentials> cachedCredentialsByName = new HashMap<>();
    private final Map<String, Long> lastRefreshByName = new HashMap<>();
    private final Map<String, Integer> activeIndexByOperation = new HashMap<>();
    private final Set<String> lockedOperations = new HashSet<>();
    private int lastSuccessfulIndex;

    /**
     * Creates a new WFC auth provider.
     *
     * @param authArguments auth arguments
     * @param apiArguments  API arguments
     */
    @Inject
    public WfcAuthProvider(AuthArguments authArguments, ApiArguments apiArguments) {
        this.authArguments = authArguments;
        this.apiArguments = apiArguments;
    }

    /**
     * Checks if WFC Auth is enabled for this run.
     *
     * @return true when a WFC auth file was supplied
     */
    public boolean isEnabled() {
        return authArguments.isWfcAuthSupplied();
    }

    /**
     * Gets the selected WFC auth entry name without resolving runtime credentials.
     *
     * @return the selected authentication entry name
     */
    public synchronized String getSelectedAuthenticationName() {
        if (!isEnabled()) {
            return "";
        }
        return selectedAuth("").getName();
    }

    /**
     * Gets WFC auth headers for the current request.
     *
     * @param okHttpClient HTTP client to use for dynamic login
     * @return headers resolved from WFC Auth
     */
    public synchronized Map<String, String> getHeaders(OkHttpClient okHttpClient) {
        return getHeaders(okHttpClient, "", "");
    }

    /**
     * Gets WFC auth headers for the specified operation.
     *
     * @param okHttpClient HTTP client to use for dynamic login
     * @param path         contract path
     * @param method       HTTP method
     * @return headers resolved from WFC Auth
     */
    public synchronized Map<String, String> getHeaders(OkHttpClient okHttpClient, String path, String method) {
        return resolveCredentials(okHttpClient, operationKey(path, method)).headers();
    }

    /**
     * Gets WFC auth query parameters for the current request.
     *
     * @param okHttpClient HTTP client to use for dynamic login
     * @return query parameters resolved from WFC Auth
     */
    public synchronized Map<String, String> getQueryParams(OkHttpClient okHttpClient) {
        return getQueryParams(okHttpClient, "", "");
    }

    /**
     * Gets WFC auth query parameters for the specified operation.
     *
     * @param okHttpClient HTTP client to use for dynamic login
     * @param path         contract path
     * @param method       HTTP method
     * @return query parameters resolved from WFC Auth
     */
    public synchronized Map<String, String> getQueryParams(OkHttpClient okHttpClient, String path, String method) {
        return resolveCredentials(okHttpClient, operationKey(path, method)).queryParams();
    }

    /**
     * WFC auth header names across all candidate entries when entry switching is active,
     * or for the pinned entry when {@code --wfcAuthName} is specified, without triggering a login request.
     *
     * @return auth header names declared or implied by the WFC configuration
     */
    public synchronized Set<String> getAuthenticationHeaderNames() {
        if (!isEnabled()) {
            return Set.of();
        }

        Set<String> headerNames = new LinkedHashSet<>();
        for (AuthenticationInfo auth : candidateEntries()) {
            headers(auth.getFixedHeaders()).stream().map(Header::getName).forEach(headerNames::add);

            LoginEndpoint login = auth.getLoginEndpointAuth();
            if (login != null) {
                if (login.getToken() != null && TokenHandling.SendIn.HEADER.equals(login.getToken().getSendIn())) {
                    headerNames.add(login.getToken().getSendName());
                }
                if (Boolean.TRUE.equals(login.getExpectCookies())) {
                    headerNames.add(COOKIE);
                }
            }
        }
        return Collections.unmodifiableSet(headerNames);
    }

    /**
     * WFC auth query parameter names across all candidate entries when entry switching is active,
     * or for the pinned entry when {@code --wfcAuthName} is specified, without triggering a login request.
     *
     * @return auth query parameter names declared by the WFC configuration
     */
    public synchronized Set<String> getAuthenticationQueryParamNames() {
        if (!isEnabled()) {
            return Set.of();
        }

        Set<String> queryNames = new LinkedHashSet<>();
        for (AuthenticationInfo auth : candidateEntries()) {
            LoginEndpoint login = auth.getLoginEndpointAuth();
            if (login != null && login.getToken() != null && TokenHandling.SendIn.QUERY.equals(login.getToken().getSendIn())) {
                queryNames.add(login.getToken().getSendName());
            }
        }
        return Collections.unmodifiableSet(queryNames);
    }

    private List<AuthenticationInfo> candidateEntries() {
        AuthenticationInfo primary = selectedAuth("");
        if (StringUtils.isBlank(authArguments.getWfcAuthName()) && authEntries.size() > 1) {
            return authEntries;
        }
        return List.of(primary);
    }

    /**
     * Checks if a header name is known to be supplied by WFC Auth.
     *
     * @param header header name
     * @return true when the selected WFC Auth entry declares this auth header
     */
    public boolean isAuthenticationHeader(String header) {
        return getAuthenticationHeaderNames().stream().anyMatch(authHeader -> authHeader.equalsIgnoreCase(header));
    }

    /**
     * Records the HTTP status code of an unmutated baseline request so multi-entry WFC files
     * can lock in a working entry on 2XX or advance to the next entry on 401/403.
     *
     * @param path       contract path
     * @param method     HTTP method
     * @param statusCode HTTP response code
     */
    public synchronized void recordOutcome(String path, String method, int statusCode) {
        if (!isEnabled() || StringUtils.isNotBlank(authArguments.getWfcAuthName())) {
            return;
        }
        String opKey = operationKey(path, method);
        selectedAuth(opKey);
        if (authEntries.size() <= 1) {
            return;
        }
        int currentIdx = activeIndexByOperation.getOrDefault(opKey, lastSuccessfulIndex);
        if (statusCode >= 200 && statusCode < 300) {
            lockedOperations.add(opKey);
            activeIndexByOperation.put(opKey, currentIdx);
            lastSuccessfulIndex = currentIdx;
        } else if (!lockedOperations.contains(opKey) && (statusCode == 401 || statusCode == 403)) {
            int nextIdx = (currentIdx + 1) % authEntries.size();
            activeIndexByOperation.put(opKey, nextIdx);
        }
    }

    private static String operationKey(String path, String method) {
        return (path == null ? "" : path) + ":" + (method == null ? "" : method);
    }

    private WfcCredentials resolveCredentials(OkHttpClient okHttpClient, String opKey) {
        if (!isEnabled()) {
            return WfcCredentials.empty();
        }

        AuthenticationInfo auth = selectedAuth(opKey);
        if (auth.getLoginEndpointAuth() == null) {
            return fixedCredentials(auth);
        }
        String entryName = auth.getName();
        WfcCredentials existing = cachedCredentialsByName.get(entryName);
        long lastRefresh = lastRefreshByName.getOrDefault(entryName, 0L);
        if (existing != null && !refreshIntervalElapsed(lastRefresh)) {
            return existing;
        }

        if (okHttpClient == null) {
            throw new CatsException("WFC Auth requires an initialized HTTP client before resolving dynamic credentials");
        }

        WfcCredentials resolved = login(auth, okHttpClient);
        long now = System.currentTimeMillis();
        cachedCredentialsByName.put(entryName, resolved);
        lastRefreshByName.put(entryName, now);
        return resolved;
    }

    private AuthenticationInfo selectedAuth(String opKey) {
        File currentFile = authArguments.getWfcAuthFile();
        String currentAuthName = authArguments.getWfcAuthName();
        if (selectedAuth == null || !Objects.equals(loadedFile, currentFile) || !Objects.equals(loadedAuthName, currentAuthName)) {
            Auth config = loader.load(currentFile);
            authEntries = config.getAuth() == null ? List.of() : List.copyOf(config.getAuth());
            selectedAuth = loader.select(config, currentAuthName);
            loadedFile = currentFile;
            loadedAuthName = currentAuthName;
            cachedCredentialsByName.clear();
            lastRefreshByName.clear();
            activeIndexByOperation.clear();
            lockedOperations.clear();
            lastSuccessfulIndex = 0;
        }
        if (StringUtils.isBlank(currentAuthName) && authEntries.size() > 1 && StringUtils.isNotBlank(opKey) && !":".equals(opKey)) {
            int idx = activeIndexByOperation.getOrDefault(opKey, lastSuccessfulIndex);
            return authEntries.get(Math.floorMod(idx, authEntries.size()));
        }
        return selectedAuth;
    }

    private WfcCredentials fixedCredentials(AuthenticationInfo auth) {
        return new WfcCredentials(fixedHeaders(auth), Map.of());
    }

    private WfcCredentials login(AuthenticationInfo auth, OkHttpClient okHttpClient) {
        LoginEndpoint login = auth.getLoginEndpointAuth();
        String loginUrl = loginUrl(login);
        String payload = loginPayload(login);
        Request request = loginRequest(login, loginUrl, payload);

        // Disable automatic redirect following for cookie logins so the Set-Cookie header on a 302 login
        // response is captured directly without making an unauthenticated follow-up request that could
        // overwrite the session cookie or mask a redirect to /login?error.
        OkHttpClient clientToUse = Boolean.TRUE.equals(login.getExpectCookies())
                ? okHttpClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
                : okHttpClient;

        logger.note("Running WFC Auth login for entry {} at {}", auth.getName(), loginUrl);
        try (Response response = clientToUse.newCall(request).execute()) {
            BoundedResponseBodyReader.CapturedBody capturedBody = BoundedResponseBodyReader.read(response, apiArguments.getMaxResponseBytes());
            if (capturedBody.truncated()) {
                throw new CatsException("WFC Auth login response exceeded --maxResponseBytes=" + apiArguments.getMaxResponseBytes());
            }
            String responseBody = capturedBody.body();
            List<String> cookies = response.headers(SET_COOKIE);
            boolean cookieRedirect = Boolean.TRUE.equals(login.getExpectCookies()) && login.getToken() == null && response.code() >= 300 && response.code() < 400 && !cookies.isEmpty() && !isErrorRedirect(response);
            if (!response.isSuccessful() && !cookieRedirect) {
                throw new CatsException("WFC Auth login failed for entry %s with HTTP %s and body %s".formatted(auth.getName(), response.code(), responseBody));
            }

            Map<String, String> headers = fixedHeaders(auth);
            Map<String, String> queryParams = new LinkedHashMap<>();

            if (login.getToken() != null) {
                addTokenCredential(login.getToken(), response, responseBody, headers, queryParams);
            }
            if (Boolean.TRUE.equals(login.getExpectCookies())) {
                headers.put(COOKIE, extractCookieHeader(cookies, auth.getName()));
            }

            return new WfcCredentials(headers, queryParams);
        } catch (IOException e) {
            throw new CatsException("WFC Auth login failed for entry " + auth.getName(), e);
        }
    }

    private boolean isErrorRedirect(Response response) {
        String location = response.header(LOCATION);
        return location != null && location.toLowerCase(Locale.ROOT).contains("error");
    }

    private Request loginRequest(LoginEndpoint login, String loginUrl, String payload) {
        Request.Builder builder = new Request.Builder().url(loginUrl);
        for (Header header : headers(login.getHeaders())) {
            builder.addHeader(header.getName(), header.getValue());
        }

        RequestBody requestBody = requestBody(login, payload);
        builder.method(login.getVerb().value(), requestBody);
        return builder.build();
    }

    private RequestBody requestBody(LoginEndpoint login, String payload) {
        boolean hasPayload = payload != null;
        boolean methodRequiresBody = Set.of(LoginEndpoint.HttpVerb.POST, LoginEndpoint.HttpVerb.PUT, LoginEndpoint.HttpVerb.PATCH).contains(login.getVerb());
        boolean methodAllowsBody = methodRequiresBody || (LoginEndpoint.HttpVerb.DELETE.equals(login.getVerb()) && hasPayload);

        if (!methodAllowsBody) {
            return null;
        }

        String contentType = StringUtils.defaultIfBlank(login.getContentType(), APPLICATION_JSON);
        byte[] payloadBytes = StringUtils.defaultString(payload).getBytes(StandardCharsets.UTF_8);
        return RequestBody.create(payloadBytes, MediaType.parse(contentType));
    }

    private String loginPayload(LoginEndpoint login) {
        if (login.getPayloadRaw() != null) {
            return login.getPayloadRaw();
        }
        if (login.getPayloadUserPwd() == null) {
            return null;
        }

        String contentType = StringUtils.defaultIfBlank(login.getContentType(), APPLICATION_JSON).toLowerCase(Locale.ROOT);
        PayloadUsernamePassword payload = login.getPayloadUserPwd();
        if (contentType.contains(APPLICATION_FORM_URLENCODED)) {
            return formEntry(payload.getUsernameField(), payload.getUsername()) + "&" + formEntry(payload.getPasswordField(), payload.getPassword());
        }
        if (contentType.contains("json")) {
            Map<String, String> body = new LinkedHashMap<>();
            body.put(payload.getUsernameField(), payload.getUsername());
            body.put(payload.getPasswordField(), payload.getPassword());
            try {
                return jsonMapper.writeValueAsString(body);
            } catch (IOException e) {
                throw new CatsException("Unable to create WFC username/password JSON payload", e);
            }
        }

        throw new CatsException("Unsupported WFC payloadUserPwd content type: " + contentType);
    }

    private String formEntry(String name, String value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void addTokenCredential(TokenHandling tokenHandling, Response response, String responseBody, Map<String, String> headers, Map<String, String> queryParams) {
        String token = extractToken(tokenHandling, response, responseBody);
        String value = StringUtils.defaultIfBlank(tokenHandling.getSendTemplate(), TOKEN_PLACEHOLDER).replace(TOKEN_PLACEHOLDER, token);

        if (TokenHandling.SendIn.HEADER.equals(tokenHandling.getSendIn())) {
            headers.put(tokenHandling.getSendName(), value);
        } else {
            queryParams.put(tokenHandling.getSendName(), value);
        }
    }

    private String extractToken(TokenHandling tokenHandling, Response response, String responseBody) {
        if (TokenHandling.ExtractFrom.HEADER.equals(tokenHandling.getExtractFrom())) {
            String value = response.header(tokenHandling.getExtractSelector());
            if (StringUtils.isBlank(value)) {
                throw new CatsException("WFC Auth token header not found: " + tokenHandling.getExtractSelector());
            }
            return value;
        }

        try {
            JsonNode tokenNode = jsonMapper.readTree(responseBody).at(tokenHandling.getExtractSelector());
            if (tokenNode.isMissingNode() || tokenNode.isNull()) {
                throw new CatsException("WFC Auth token selector not found in response body: " + tokenHandling.getExtractSelector());
            }
            return tokenNode.isValueNode() ? tokenNode.asText() : tokenNode.toString();
        } catch (IOException e) {
            throw new CatsException("Unable to parse WFC Auth login response as JSON", e);
        }
    }

    private String extractCookieHeader(List<String> setCookies, String authName) {
        if (setCookies == null || setCookies.isEmpty()) {
            throw new CatsException("WFC Auth entry " + authName + " expected cookies, but login response did not include Set-Cookie");
        }

        return setCookies.stream()
                .filter(StringUtils::isNotBlank)
                .map(cookie -> cookie.split(";", 2)[0])
                .collect(Collectors.joining("; "));
    }

    private String loginUrl(LoginEndpoint login) {
        if (StringUtils.isNotBlank(login.getExternalEndpointURL())) {
            return login.getExternalEndpointURL();
        }

        String server = apiArguments.getServer();
        if (StringUtils.isBlank(server)) {
            throw new CatsException("WFC Auth endpoint requires a resolved --server URL");
        }

        String normalizedServer = Strings.CS.removeEnd(server, "/");
        HttpUrl baseUrl = HttpUrl.parse(normalizedServer + "/");
        if (baseUrl != null) {
            HttpUrl resolved = baseUrl.resolve(login.getEndpoint());
            if (resolved != null) {
                return resolved.toString();
            }
        }
        return normalizedServer + (login.getEndpoint().startsWith("/") ? login.getEndpoint() : "/" + login.getEndpoint());
    }

    private Map<String, String> fixedHeaders(AuthenticationInfo auth) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header fixedHeader : headers(auth.getFixedHeaders())) {
            headers.put(fixedHeader.getName(), fixedHeader.getValue());
        }
        return headers;
    }

    private List<Header> headers(List<Header> headers) {
        return headers == null ? List.of() : headers;
    }

    private boolean refreshIntervalElapsed(long lastRefreshMillis) {
        int refreshInterval = authArguments.getAuthRefreshInterval();
        return refreshInterval > 0 && (System.currentTimeMillis() - lastRefreshMillis) / 1000 >= refreshInterval;
    }

    /**
     * Adds WFC auth query params to the supplied URL.
     *
     * @param url          starting URL
     * @param okHttpClient HTTP client to use for dynamic login
     * @return URL with WFC auth query params applied
     */
    public String applyQueryParams(String url, OkHttpClient okHttpClient) {
        return applyQueryParams(url, okHttpClient, "", "");
    }

    /**
     * Adds WFC auth query params to the supplied URL for the specified operation.
     *
     * @param url          starting URL
     * @param okHttpClient HTTP client to use for dynamic login
     * @param path         contract path
     * @param method       HTTP method
     * @return URL with WFC auth query params applied
     */
    public String applyQueryParams(String url, OkHttpClient okHttpClient, String path, String method) {
        Map<String, String> queryParams = getQueryParams(okHttpClient, path, method);
        if (queryParams.isEmpty()) {
            return url;
        }

        HttpUrl.Builder builder = HttpUrl.get(url).newBuilder();
        queryParams.forEach(builder::setQueryParameter);
        return builder.build().toString();
    }
}
