package com.endava.cats.auth.wfc;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.exception.CatsException;
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
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
@ApplicationScoped
public class WfcAuthProvider {
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String COOKIE = "Cookie";
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
    private WfcCredentials cachedCredentials;
    private long lastRefreshTimeMillis;

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
        return selectedAuth().getName();
    }

    /**
     * Gets WFC auth headers for the current request.
     *
     * @param okHttpClient HTTP client to use for dynamic login
     * @return headers resolved from WFC Auth
     */
    public synchronized Map<String, String> getHeaders(OkHttpClient okHttpClient) {
        return resolveCredentials(okHttpClient).headers();
    }

    /**
     * Gets WFC auth query parameters for the current request.
     *
     * @param okHttpClient HTTP client to use for dynamic login
     * @return query parameters resolved from WFC Auth
     */
    public synchronized Map<String, String> getQueryParams(OkHttpClient okHttpClient) {
        return resolveCredentials(okHttpClient).queryParams();
    }

    /**
     * WFC auth header names, without triggering a login request.
     *
     * @return auth header names declared or implied by the selected WFC entry
     */
    public synchronized Set<String> getAuthenticationHeaderNames() {
        if (!isEnabled()) {
            return Set.of();
        }

        AuthenticationInfo auth = selectedAuth();
        Set<String> headerNames = new LinkedHashSet<>();
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
        return Set.copyOf(headerNames);
    }

    /**
     * WFC auth query parameter names, without triggering a login request.
     *
     * @return auth query parameter names declared by the selected WFC entry
     */
    public synchronized Set<String> getAuthenticationQueryParamNames() {
        if (!isEnabled()) {
            return Set.of();
        }

        LoginEndpoint login = selectedAuth().getLoginEndpointAuth();
        if (login == null || login.getToken() == null || !TokenHandling.SendIn.QUERY.equals(login.getToken().getSendIn())) {
            return Set.of();
        }
        return Set.of(login.getToken().getSendName());
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

    private WfcCredentials resolveCredentials(OkHttpClient okHttpClient) {
        if (!isEnabled()) {
            return WfcCredentials.empty();
        }

        AuthenticationInfo auth = selectedAuth();
        if (auth.getLoginEndpointAuth() == null) {
            return fixedCredentials(auth);
        }

        if (cachedCredentials != null && !refreshIntervalElapsed()) {
            return cachedCredentials;
        }

        if (okHttpClient == null) {
            throw new CatsException("WFC Auth requires an initialized HTTP client before resolving dynamic credentials");
        }

        cachedCredentials = login(auth, okHttpClient);
        lastRefreshTimeMillis = System.currentTimeMillis();
        return cachedCredentials;
    }

    private AuthenticationInfo selectedAuth() {
        File currentFile = authArguments.getWfcAuthFile();
        String currentAuthName = authArguments.getWfcAuthName();
        if (selectedAuth == null || !Objects.equals(loadedFile, currentFile) || !Objects.equals(loadedAuthName, currentAuthName)) {
            Auth config = loader.load(currentFile);
            selectedAuth = loader.select(config, currentAuthName);
            loadedFile = currentFile;
            loadedAuthName = currentAuthName;
            cachedCredentials = null;
            lastRefreshTimeMillis = 0;
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

        logger.note("Running WFC Auth login for entry {} at {}", auth.getName(), loginUrl);
        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new CatsException("WFC Auth login failed for entry %s with HTTP %s and body %s".formatted(auth.getName(), response.code(), responseBody));
            }

            Map<String, String> headers = fixedHeaders(auth);
            Map<String, String> queryParams = new LinkedHashMap<>();

            if (login.getToken() != null) {
                addTokenCredential(login.getToken(), response, responseBody, headers, queryParams);
            }
            if (Boolean.TRUE.equals(login.getExpectCookies())) {
                headers.put(COOKIE, extractCookieHeader(response.headers(SET_COOKIE), auth.getName()));
            }

            return new WfcCredentials(headers, queryParams);
        } catch (IOException e) {
            throw new CatsException("WFC Auth login failed for entry " + auth.getName(), e);
        }
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
        if (login.getEndpoint().startsWith("/")) {
            return normalizedServer + login.getEndpoint();
        }
        return normalizedServer + "/" + login.getEndpoint();
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

    private boolean refreshIntervalElapsed() {
        int refreshInterval = authArguments.getAuthRefreshInterval();
        return refreshInterval > 0 && (System.currentTimeMillis() - lastRefreshTimeMillis) / 1000 >= refreshInterval;
    }

    /**
     * Adds WFC auth query params to the supplied URL.
     *
     * @param url          starting URL
     * @param okHttpClient HTTP client to use for dynamic login
     * @return URL with WFC auth query params applied
     */
    public String applyQueryParams(String url, OkHttpClient okHttpClient) {
        Map<String, String> queryParams = getQueryParams(okHttpClient);
        if (queryParams.isEmpty()) {
            return url;
        }

        HttpUrl.Builder builder = HttpUrl.get(url).newBuilder();
        queryParams.forEach(builder::setQueryParameter);
        return builder.build().toString();
    }
}
