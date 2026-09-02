package com.endava.cats.auth.wfc;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.exception.CatsException;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class WfcAuthProviderTest {
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
        Assertions.assertThat(request.getValue().url().toString()).isEqualTo("https://example.com/wfc-login");
        Assertions.assertThat(request.getValue().method()).isEqualTo("POST");
        Assertions.assertThat(requestBody(request.getValue())).isEqualTo("{\"username\":\"cats\",\"password\":\"secret\"}");
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
        Assertions.assertThat(authenticatedUrl).contains("keep=yes", "access_token=abc%20123");
        Assertions.assertThat(authenticatedUrl).doesNotContain("access_token=old");
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
        Assertions.assertThat(request.getValue().url().toString()).isEqualTo("https://example.com/base/login");
        Assertions.assertThat(request.getValue().header("X-Login")).isEqualTo("yes");
        Assertions.assertThat(requestBody(request.getValue())).isEqualTo("user=user+name&password=p%40ss+word");
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
