package com.endava.cats.auth.wfc;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.exception.CatsException;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@QuarkusTest
class WfcAuthProviderTest {
    @Inject
    WfcAuthProvider interceptedProvider;
    @Inject
    AuthArguments interceptedAuthArguments;
    @Inject
    FilterArguments filterArguments;
    private AuthArguments authArguments;
    private ApiArguments apiArguments;
    private WfcAuthProvider provider;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        authArguments = Mockito.mock(AuthArguments.class);
        apiArguments = Mockito.mock(ApiArguments.class);
        provider = new WfcAuthProvider(authArguments, apiArguments);
        ReflectionTestUtils.setField(filterArguments, "dryRun", false);
    }

    @Test
    void shouldReturnEmptyCredentialsWhenWfcAuthIsDisabled() {
        Mockito.when(authArguments.isWfcAuthSupplied()).thenReturn(false);

        Assertions.assertThat(provider.isEnabled()).isFalse();
        Assertions.assertThat(provider.getSelectedAuthenticationName()).isEmpty();
        Assertions.assertThat(provider.getHeaders(null)).isEmpty();
        Assertions.assertThat(provider.getQueryParams(null)).isEmpty();
        Assertions.assertThat(provider.getAuthenticationHeaderNames()).isEmpty();
        Assertions.assertThat(provider.getAuthenticationQueryParamNames()).isEmpty();
        Assertions.assertThat(provider.isAuthenticationHeader("Authorization")).isFalse();
        Assertions.assertThat(provider.applyQueryParams("https://example.com/items", null))
                .isEqualTo("https://example.com/items");
    }

    @Test
    void shouldResolveStaticHeadersWithoutAnHttpClient() {
        enable(new File("src/test/resources/wfc-static-auth.yml"), "static");

        Assertions.assertThat(provider.getSelectedAuthenticationName()).isEqualTo("static");
        Assertions.assertThat(provider.getHeaders(null)).containsEntry("X-Session-Token", "static-secret");
        Assertions.assertThat(provider.getQueryParams(null)).isEmpty();
        Assertions.assertThat(provider.getAuthenticationHeaderNames()).containsExactly("X-Session-Token");
        Assertions.assertThat(provider.isAuthenticationHeader("x-session-token")).isTrue();
        Assertions.assertThat(provider.isAuthenticationHeader("Authorization")).isFalse();
    }

    @Test
    void shouldLoginOnceAndCacheBodyTokenCredentials() throws IOException {
        enable(new File("src/test/resources/wfc-token-auth.yml"), "cats");
        Mockito.when(apiArguments.getServer()).thenReturn("https://example.com/");
        OkHttpClient client = successfulClient("{\"accessToken\":\"secret-token\"}", Map.of());

        Assertions.assertThat(provider.getHeaders(client)).containsEntry("Authorization", "Bearer secret-token");
        Assertions.assertThat(provider.getQueryParams(client)).isEmpty();
        Assertions.assertThat(provider.getAuthenticationHeaderNames()).containsExactly("Authorization");
        Assertions.assertThat(provider.getAuthenticationQueryParamNames()).isEmpty();

        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        Mockito.verify(client).newCall(request.capture());
        Assertions.assertThat(request.getValue().url()).hasToString("https://example.com/wfc-login");
        Assertions.assertThat(request.getValue().method()).isEqualTo("POST");
        Assertions.assertThat(requestBody(request.getValue())).isEqualTo("{\"username\":\"cats\",\"password\":\"secret\"}");
    }

    @Test
    void shouldNotRunDynamicLoginDuringDryRun() {
        ReflectionTestUtils.setField(interceptedAuthArguments, "wfcAuthFile", new File("src/test/resources/wfc-token-auth.yml"));
        ReflectionTestUtils.setField(interceptedAuthArguments, "wfcAuthName", "cats");
        ReflectionTestUtils.setField(filterArguments, "dryRun", true);
        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        try {
            Assertions.assertThat(interceptedProvider.getAuthenticationHeaderNames()).containsExactly("Authorization");
            Assertions.assertThat(interceptedProvider.getHeaders(client)).isEmpty();
            Assertions.assertThat(interceptedProvider.getQueryParams(client)).isEmpty();
            Mockito.verifyNoInteractions(client);
        } finally {
            ReflectionTestUtils.setField(filterArguments, "dryRun", false);
            ReflectionTestUtils.setField(interceptedAuthArguments, "wfcAuthFile", null);
            ReflectionTestUtils.setField(interceptedAuthArguments, "wfcAuthName", null);
        }
    }

    @Test
    void shouldExtractHeaderTokenAndApplyItAsQueryParameter() throws IOException {
        File config = config("""
                auth:
                  - name: query-token
                    loginEndpointAuth:
                      externalEndpointURL: https://login.example.com/token
                      verb: GET
                      token:
                        extractFrom: header
                        extractSelector: X-Access-Token
                        sendIn: query
                        sendName: access_token
                """);
        enable(config, null);
        OkHttpClient client = successfulClient("", Map.of("X-Access-Token", "abc 123"));

        Assertions.assertThat(provider.getAuthenticationHeaderNames()).isEmpty();
        Assertions.assertThat(provider.getAuthenticationQueryParamNames()).containsExactly("access_token");
        String authenticatedUrl = provider.applyQueryParams("https://api.example.com/items?access_token=old&keep=yes", client);
        Assertions.assertThat(authenticatedUrl).contains("keep=yes", "access_token=abc%20123").doesNotContain("access_token=old");
    }

    @Test
    void shouldCreateFormPayloadAndCombineResponseCookies() throws IOException {
        File config = config("""
                auth:
                  - name: cookie-auth
                    loginEndpointAuth:
                      endpoint: login
                      verb: POST
                      contentType: application/x-www-form-urlencoded
                      payloadUserPwd:
                        username: user name
                        password: p@ss word
                        usernameField: user
                        passwordField: password
                      headers:
                        - name: X-Login
                          value: "yes"
                      expectCookies: true
                """);
        enable(config, "cookie-auth");
        Mockito.when(apiArguments.getServer()).thenReturn("https://example.com/base");
        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        OkHttpClient.Builder builder = Mockito.mock(OkHttpClient.Builder.class);
        Mockito.when(client.newBuilder()).thenReturn(builder);
        Mockito.when(builder.followRedirects(false)).thenReturn(builder);
        Mockito.when(builder.followSslRedirects(false)).thenReturn(builder);
        Mockito.when(builder.build()).thenReturn(client);
        Call call = Mockito.mock(Call.class);
        Response response = response("", Map.of());
        Mockito.when(response.isSuccessful()).thenReturn(true);
        Mockito.when(response.headers("Set-Cookie")).thenReturn(List.of("one=1; Path=/", "", "two=2; HttpOnly"));
        Mockito.when(call.execute()).thenReturn(response);
        Mockito.when(client.newCall(Mockito.any())).thenReturn(call);

        Assertions.assertThat(provider.getHeaders(client)).containsEntry("Cookie", "one=1; two=2");
        Assertions.assertThat(provider.getAuthenticationHeaderNames()).containsExactly("Cookie");

        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        Mockito.verify(client).newCall(request.capture());
        Assertions.assertThat(request.getValue().url()).hasToString("https://example.com/base/login");
        Assertions.assertThat(request.getValue().header("X-Login")).isEqualTo("yes");
        Assertions.assertThat(requestBody(request.getValue())).isEqualTo("user=user+name&password=p%40ss+word");
    }

    @Test
    void shouldHandleRealRedirectingServerForCookieLoginWithoutFollowingRedirects() throws IOException {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        server.createContext("/WebGoat/login", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=authenticated-session; Path=/WebGoat");
            exchange.getResponseHeaders().add("Location", "/WebGoat/start.mvc");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/WebGoat/start.mvc", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=anonymous-session; Path=/WebGoat");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.createContext("/login", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=anonymous-session; Path=/");
            exchange.getResponseHeaders().add("Location", "/login?error");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            OkHttpClient realClient = new OkHttpClient();

            File successConfig = config("""
                    auth:
                      - name: redirect-cookie
                        loginEndpointAuth:
                          endpoint: /WebGoat/login
                          verb: POST
                          contentType: application/x-www-form-urlencoded
                          payloadRaw: "username=u&password=p"
                          expectCookies: true
                    """);
            enable(successConfig, "redirect-cookie");
            Mockito.when(apiArguments.getServer()).thenReturn("http://localhost:" + port + "/WebGoat");
            Mockito.when(apiArguments.getMaxResponseBytes()).thenReturn(10240L);

            Assertions.assertThat(provider.getHeaders(realClient))
                    .containsEntry("Cookie", "JSESSIONID=authenticated-session");

            File errorConfig = config("""
                    auth:
                      - name: redirect-error
                        loginEndpointAuth:
                          endpoint: /login
                          verb: POST
                          contentType: application/x-www-form-urlencoded
                          payloadRaw: "username=u&password=wrong"
                          expectCookies: true
                    """);
            enable(errorConfig, "redirect-error");
            Mockito.when(apiArguments.getServer()).thenReturn("http://localhost:" + port);

            Assertions.assertThatThrownBy(() -> provider.getHeaders(realClient))
                    .isInstanceOf(CatsException.class)
                    .hasMessageContaining("HTTP 302");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRotateWfcAuthEntriesOnlyOn401And403AndReturnUnionOfHeaderAndQueryNames() throws IOException {
        File config = config("""
                auth:
                  - name: admin
                    fixedHeaders:
                      - name: Authorization
                        value: Basic admin
                  - name: customer
                    fixedHeaders:
                      - name: X-Api-Key
                        value: customer-key
                  - name: queryUser
                    loginEndpointAuth:
                      externalEndpointURL: https://login.example.com/token
                      verb: GET
                      expectCookies: true
                      token:
                        extractFrom: header
                        extractSelector: X-Token
                        sendIn: query
                        sendName: api_token
                """);
        enable(config, null);

        Assertions.assertThat(provider.getAuthenticationHeaderNames())
                .containsExactly("Authorization", "X-Api-Key", "Cookie");
        Assertions.assertThat(provider.getAuthenticationQueryParamNames())
                .containsExactly("api_token");
        Assertions.assertThat(provider.isAuthenticationHeader("x-api-key")).isTrue();
        Assertions.assertThat(provider.isAuthenticationHeader("cookie")).isTrue();

        Assertions.assertThat(provider.getHeaders(null, "/customer", "GET"))
                .containsEntry("Authorization", "Basic admin");

        provider.recordOutcome("/customer", "GET", 404);
        provider.recordOutcome("/customer", "GET", 500);
        Assertions.assertThat(provider.getHeaders(null, "/customer", "GET"))
                .containsEntry("Authorization", "Basic admin");

        provider.recordOutcome("/customer", "GET", 403);
        Assertions.assertThat(provider.getHeaders(null, "/customer", "GET"))
                .containsEntry("X-Api-Key", "customer-key");

        provider.recordOutcome("/customer", "GET", 200);
        provider.recordOutcome("/customer", "GET", 401);
        Assertions.assertThat(provider.getHeaders(null, "/customer", "GET"))
                .containsEntry("X-Api-Key", "customer-key");
        Assertions.assertThat(provider.getHeaders(null, "/customer/cart", "GET"))
                .containsEntry("X-Api-Key", "customer-key");
    }

    @Test
    void shouldRejectInvalidDynamicLoginResponses() throws IOException {
        enable(new File("src/test/resources/wfc-token-auth.yml"), "cats");
        Mockito.when(apiArguments.getServer()).thenReturn("https://example.com");

        Assertions.assertThatThrownBy(() -> provider.getHeaders(null))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("initialized HTTP client");

        OkHttpClient unsuccessful = client(response("denied", Map.of()), false, null);
        Assertions.assertThatThrownBy(() -> provider.getHeaders(unsuccessful))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageContaining("denied");

        OkHttpClient invalidJson = successfulClient("not-json", Map.of());
        Assertions.assertThatThrownBy(() -> provider.getHeaders(invalidJson))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("parse WFC Auth login response");
    }

    @Test
    void shouldReportTransportAndConfigurationErrors() throws IOException {
        enable(new File("src/test/resources/wfc-token-auth.yml"), "cats");
        Mockito.when(apiArguments.getServer()).thenReturn(" ");
        Assertions.assertThatThrownBy(() -> provider.getHeaders(Mockito.mock(OkHttpClient.class)))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("resolved --server URL");

        Mockito.when(apiArguments.getServer()).thenReturn("https://example.com");
        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        Mockito.when(client.newCall(Mockito.any())).thenReturn(call);
        Mockito.when(call.execute()).thenThrow(new IOException("offline"));
        Assertions.assertThatThrownBy(() -> provider.getHeaders(client))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("login failed")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void shouldExposeNoSessionDataWhenDisabled() {
        Mockito.when(authArguments.isWfcAuthSupplied()).thenReturn(false);

        provider.requestReauthentication("user1");
        provider.reportSessionLost("user1");
        provider.recordSessionHealthy("user1");

        Assertions.assertThat(provider.getActiveEntryName("/orders", "GET")).isEmpty();
    }

    @Test
    void shouldQuarantineSuspectOperationWhenRotatingAwayFromDeadEntry() throws IOException {
        enable(config("""
                auth:
                  - name: admin
                    fixedHeaders:
                      - name: Authorization
                        value: Basic admin
                  - name: customer
                    fixedHeaders:
                      - name: X-Api-Key
                        value: customer-key
                """), null);
        provider.recordOutcome("/orders", "GET", 200);
        provider.recordOutcome("/user/deleteAccount", "POST", 200);

        provider.reportSessionLost("admin", "/user/deleteAccount", "POST");

        Assertions.assertThat(provider.getActiveEntryName("/orders", "GET")).isEqualTo("customer");
        Assertions.assertThat(provider.getActiveEntryName("/user/deleteAccount", "POST")).isEqualTo("admin");
    }

    @Test
    void shouldLogInAgainOnceWhenSessionIsLostAndRotateWhenItStaysLost() throws IOException {
        enable(multiUserConfig(), null);
        Mockito.when(apiArguments.getServer()).thenReturn("https://example.com");
        OkHttpClient client = successfulClient("{\"accessToken\":\"token\"}", Map.of());

        Assertions.assertThat(provider.getHeaders(client, "/orders", "GET")).containsEntry("Authorization", "Bearer token");
        provider.recordOutcome("/orders", "GET", 200);
        Assertions.assertThat(provider.getActiveEntryName("/orders", "GET")).isEqualTo("user1");

        provider.reportSessionLost("user1");
        provider.recordSessionHealthy("user1");
        provider.getHeaders(client, "/orders", "GET");
        provider.recordSessionHealthy("user1");
        provider.getHeaders(client, "/orders", "GET");
        Mockito.verify(client, Mockito.times(2)).newCall(Mockito.any());

        provider.reportSessionLost("user1");
        provider.reportSessionLost("user1");
        Assertions.assertThat(provider.getActiveEntryName("/orders", "GET")).isEqualTo("user2");
        Assertions.assertThat(provider.getActiveEntryName("/unbound", "GET")).isEqualTo("user2");

        provider.recordOutcome("/orders", "GET", 401);
        Assertions.assertThat(provider.getActiveEntryName("/orders", "GET")).isEqualTo("static");
        provider.reportSessionLost("user1");
        provider.requestReauthentication("user1");
        provider.requestReauthentication("static");
        provider.requestReauthentication("missing");
        provider.reportSessionLost("missing");
        provider.recordSessionHealthy("user2");
        Assertions.assertThat(provider.getHeaders(client, "/orders", "GET")).containsEntry("X-Api-Key", "static-key");
    }

    @Test
    void shouldRotateInsteadOfFailingWhenReloginFailsAfterASuccessfulLogin() throws IOException {
        enable(multiUserConfig(), null);
        Mockito.when(apiArguments.getServer()).thenReturn("https://example.com");
        OkHttpClient client = clientSucceedingOnceThenFailing(new IOException("account deleted"));

        Assertions.assertThat(provider.getHeaders(client, "/orders", "GET")).containsEntry("Authorization", "Bearer token");
        provider.requestReauthentication("user1");

        Assertions.assertThat(provider.getHeaders(client, "/orders", "GET")).containsEntry("X-Api-Key", "static-key");
        Assertions.assertThat(provider.getActiveEntryName("/orders", "GET")).isEqualTo("static");
        provider.recordOutcome("/orders", "GET", 403);
        Assertions.assertThat(provider.getActiveEntryName("/orders", "GET")).isEqualTo("external");
        provider.recordOutcome("/orders", "GET", 403);
        Assertions.assertThat(provider.getActiveEntryName("/orders", "GET")).isEqualTo("static");
        Mockito.verify(client, Mockito.times(3)).newCall(Mockito.any());
    }

    @Test
    void shouldStopLoggingInWhenTheOnlyEntryCannotLogInAgain() throws IOException {
        enable(new File("src/test/resources/wfc-token-auth.yml"), "cats");
        Mockito.when(apiArguments.getServer()).thenReturn("https://example.com");
        OkHttpClient client = clientSucceedingOnceThenFailing(new IOException("down"));

        provider.getHeaders(client);
        provider.requestReauthentication("cats");

        Assertions.assertThat(provider.getHeaders(client)).isEmpty();
        Assertions.assertThat(provider.getHeaders(client)).isEmpty();
        Mockito.verify(client, Mockito.times(2)).newCall(Mockito.any());
    }

    @Test
    void shouldKeepStaleCredentialsWhenRefreshFailsWithoutAlternativeEntry() throws IOException {
        enable(new File("src/test/resources/wfc-token-auth.yml"), "cats");
        Mockito.when(apiArguments.getServer()).thenReturn("https://example.com");
        Mockito.when(authArguments.getAuthRefreshInterval()).thenReturn(1);
        OkHttpClient client = clientSucceedingOnceThenFailing(new IOException("down"));

        provider.getHeaders(client);
        lastRefreshByName().put("cats", 0L);

        Assertions.assertThat(provider.getHeaders(client)).containsEntry("Authorization", "Bearer token");
    }

    @Test
    void shouldMarkFixedHeaderEntriesUnusableAndUnlockTheirOperations() throws IOException {
        enable(config("""
                auth:
                  - name: admin
                    fixedHeaders:
                      - name: Authorization
                        value: Basic admin
                  - name: customer
                    fixedHeaders:
                      - name: X-Api-Key
                        value: customer-key
                  - name: guest
                    fixedHeaders:
                      - name: X-Guest
                        value: guest
                """), null);
        provider.recordOutcome("/customer", "GET", 200);

        provider.reportSessionLost("admin");
        Assertions.assertThat(provider.getActiveEntryName("/customer", "GET")).isEqualTo("customer");
        provider.recordOutcome("/customer", "GET", 401);

        Assertions.assertThat(provider.getActiveEntryName("/customer", "GET")).isEqualTo("guest");
        Assertions.assertThat(provider.getHeaders(null, "/other", "GET")).containsEntry("X-Api-Key", "customer-key");
    }

    @Test
    void shouldKeepUnrelatedBindingsAndStopRotatingWhenEveryEntryIsUnusable() throws IOException {
        enable(config("""
                auth:
                  - name: admin
                    fixedHeaders:
                      - name: Authorization
                        value: Basic admin
                  - name: customer
                    fixedHeaders:
                      - name: X-Api-Key
                        value: customer-key
                  - name: guest
                    fixedHeaders:
                      - name: X-Guest
                        value: guest
                """), null);
        provider.recordOutcome("/admin", "GET", 200);
        provider.recordOutcome("/guest", "GET", 403);
        provider.recordOutcome("/guest", "GET", 403);
        Assertions.assertThat(provider.getActiveEntryName("/guest", "GET")).isEqualTo("guest");

        provider.reportSessionLost("guest");
        Assertions.assertThat(provider.getActiveEntryName("/admin", "GET")).isEqualTo("admin");
        Assertions.assertThat(provider.getActiveEntryName("/guest", "GET")).isEqualTo("admin");

        provider.reportSessionLost("admin");
        provider.reportSessionLost("customer");
        provider.recordOutcome("/fresh", "GET", 401);
        provider.requestReauthentication("");
        provider.recordSessionHealthy("admin");

        Assertions.assertThat(provider.getActiveEntryName("/fresh", "GET")).isEqualTo("customer");
        Assertions.assertThat(provider.getHeaders(null, "/fresh", "GET")).containsEntry("X-Api-Key", "customer-key");
    }

    @Test
    void shouldNotRotateWhenEntryIsPinned() throws IOException {
        enable(multiUserConfig(), "user1");

        provider.reportSessionLost("user1");
        provider.reportSessionLost("user1");

        Assertions.assertThat(provider.getActiveEntryName("/orders", "GET")).isEqualTo("user1");
    }

    private File multiUserConfig() throws IOException {
        return config("""
                auth:
                  - name: user1
                    loginEndpointAuth:
                      endpoint: /api/auth/login
                      verb: POST
                      contentType: application/json
                      payloadUserPwd:
                        username: u1
                        password: p1
                        usernameField: login
                        passwordField: secret
                      token:
                        extractFrom: body
                        extractSelector: /accessToken
                        sendIn: header
                        sendName: Authorization
                        sendTemplate: "Bearer {token}"
                  - name: user2
                    loginEndpointAuth:
                      endpoint: /api/auth/login
                      verb: POST
                      contentType: application/json
                      payloadUserPwd:
                        username: u2
                        password: p2
                        usernameField: login
                        passwordField: secret
                      token:
                        extractFrom: body
                        extractSelector: /accessToken
                        sendIn: header
                        sendName: Authorization
                        sendTemplate: "Bearer {token}"
                  - name: static
                    fixedHeaders:
                      - name: X-Api-Key
                        value: static-key
                  - name: external
                    loginEndpointAuth:
                      externalEndpointURL: https://login.example.com/token
                      verb: GET
                      token:
                        extractFrom: header
                        extractSelector: X-Token
                        sendIn: header
                        sendName: X-Token
                """);
    }

    private OkHttpClient clientSucceedingOnceThenFailing(IOException failure) throws IOException {
        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        Response response = response("{\"accessToken\":\"token\"}", Map.of());
        Mockito.when(response.isSuccessful()).thenReturn(true);
        Mockito.when(response.code()).thenReturn(200);
        Mockito.when(client.newCall(Mockito.any())).thenReturn(call);
        Mockito.when(call.execute()).thenReturn(response).thenThrow(failure);
        return client;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> lastRefreshByName() {
        return (Map<String, Long>) ReflectionTestUtils.getField(provider, "lastRefreshByName");
    }

    private void enable(File file, String name) {
        Mockito.when(authArguments.isWfcAuthSupplied()).thenReturn(true);
        Mockito.when(authArguments.getWfcAuthFile()).thenReturn(file);
        Mockito.when(authArguments.getWfcAuthName()).thenReturn(name);
    }

    private File config(String yaml) throws IOException {
        Path file = tempDir.resolve("wfc-auth.yml");
        Files.writeString(file, yaml);
        return file.toFile();
    }

    private OkHttpClient successfulClient(String body, Map<String, String> headers) throws IOException {
        return client(response(body, headers), true, null);
    }

    private OkHttpClient client(Response response, boolean successful, IOException failure) throws IOException {
        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        Mockito.when(client.newCall(Mockito.any())).thenReturn(call);
        if (failure == null) {
            Mockito.when(response.isSuccessful()).thenReturn(successful);
            Mockito.when(response.code()).thenReturn(successful ? 200 : 401);
            Mockito.when(call.execute()).thenReturn(response);
        } else {
            Mockito.when(call.execute()).thenThrow(failure);
        }
        return client;
    }

    private Response response(String body, Map<String, String> headers) throws IOException {
        Response response = Mockito.mock(Response.class);
        ResponseBody responseBody = Mockito.mock(ResponseBody.class);
        Mockito.when(response.body()).thenReturn(responseBody);
        Mockito.when(responseBody.string()).thenReturn(body);
        headers.forEach((name, value) -> Mockito.when(response.header(name)).thenReturn(value));
        return response;
    }

    private String requestBody(Request request) throws IOException {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }
}
