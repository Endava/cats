package com.endava.cats.auth.wfc;

import com.endava.cats.exception.CatsException;
import com.webfuzzing.commons.auth.Auth;
import com.webfuzzing.commons.auth.AuthenticationInfo;
import com.webfuzzing.commons.auth.Header;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@QuarkusTest
class WfcAuthConfigLoaderTest {
    private final WfcAuthConfigLoader loader = new WfcAuthConfigLoader();

    @TempDir
    Path tempDir;

    @Test
    void shouldMergeTemplateBeforeValidatingAuthEntries() {
        Auth config = loader.load(new File("src/test/resources/wfc-token-auth.yml"));
        AuthenticationInfo selected = loader.select(config, null);

        Assertions.assertThat(selected.getName()).isEqualTo("cats");
        Assertions.assertThat(selected.getLoginEndpointAuth().getEndpoint()).isEqualTo("/wfc-login");
        Assertions.assertThat(selected.getLoginEndpointAuth().getVerb().value()).isEqualTo("POST");
        Assertions.assertThat(selected.getLoginEndpointAuth().getContentType()).isEqualTo("application/json");
        Assertions.assertThat(selected.getLoginEndpointAuth().getPayloadUserPwd().getUsername()).isEqualTo("cats");
        Assertions.assertThat(selected.getLoginEndpointAuth().getToken().getSendName()).isEqualTo("Authorization");
    }

    @Test
    void shouldSelectAuthByName() {
        Auth config = loader.load(new File("src/test/resources/wfc-static-auth.yml"));

        AuthenticationInfo selected = loader.select(config, "static");
        Header header = selected.getFixedHeaders().getFirst();

        Assertions.assertThat(header.getName()).isEqualTo("X-Session-Token");
        Assertions.assertThat(header.getValue()).isEqualTo("static-secret");
    }

    @Test
    void shouldLoadWfcDatasetExamples() {
        AuthenticationInfo staticToken = loader.select(loader.load(new File("files/wfc-auth-static-token.yml")), "foo");
        Assertions.assertThat(staticToken.getFixedHeaders().getFirst().getName()).isEqualTo("Authorization");

        AuthenticationInfo dynamicToken = loader.select(loader.load(new File("files/wfc-auth-dynamic-token.yml")), "admin");
        Assertions.assertThat(dynamicToken.getLoginEndpointAuth().getEndpoint()).isEqualTo("/api/auth/signin");
        Assertions.assertThat(dynamicToken.getLoginEndpointAuth().getToken().getExtractSelector()).isEqualTo("/accessToken");
        Assertions.assertThat(dynamicToken.getLoginEndpointAuth().getToken().getSendName()).isEqualTo("Authorization");

        AuthenticationInfo cookie = loader.select(loader.load(new File("files/wfc-auth-cookie.yml")), "user1");
        Assertions.assertThat(cookie.getLoginEndpointAuth().getContentType()).isEqualTo("application/x-www-form-urlencoded");
        Assertions.assertThat(cookie.getLoginEndpointAuth().getExpectCookies()).isTrue();

        AuthenticationInfo externalToken = loader.select(loader.load(new File("files/wfc-auth-external-token.yml")), "ADMIN_1");
        Assertions.assertThat(externalToken.getLoginEndpointAuth().getExternalEndpointURL())
                .isEqualTo("http://localhost:8081/realms/microcks/protocol/openid-connect/token");
        Assertions.assertThat(externalToken.getLoginEndpointAuth().getToken().getExtractSelector()).isEqualTo("/access_token");
    }

    @Test
    void shouldRejectEntriesRequiringMockHandling() {
        Assertions.assertThatThrownBy(() -> loader.load(new File("src/test/resources/wfc-mock-auth.yml")))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("requires mock handling");
    }

    @Test
    void shouldFailWhenRequestedNameDoesNotExist() {
        Auth config = loader.load(new File("src/test/resources/wfc-static-auth.yml"));

        Assertions.assertThatThrownBy(() -> loader.select(config, "missing"))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("No WFC auth entry named missing");
    }

    @Test
    void shouldLoadSchemaVersionConfigsAndDefaultTokenTemplate() throws IOException {
        Auth config = loader.load(config("""
                schemaVersion: "1.0"
                configs:
                  environment: local
                  attempts: 2
                auth:
                  - name: first
                    loginEndpointAuth:
                      externalEndpointURL: https://example.com/login
                      verb: GET
                      token:
                        extractFrom: header
                        extractSelector: X-Token
                        sendIn: query
                        sendName: token
                """));

        AuthenticationInfo selected = loader.select(config, " ");
        Assertions.assertThat(config.getSchemaVersion()).isEqualTo("1.0");
        Assertions.assertThat(config.getConfigs().getAdditionalProperties())
                .containsEntry("environment", "local")
                .containsEntry("attempts", "2");
        Assertions.assertThat(selected.getLoginEndpointAuth().getToken().getSendTemplate()).isEqualTo("{token}");
    }

    @Test
    void shouldRejectMalformedOrIncompleteDocuments() throws IOException {
        Map<String, String> invalidDocuments = Map.ofEntries(
                Map.entry("auth array", "name: value"),
                Map.entry("at least one auth entry", "auth: []"),
                Map.entry("authTemplate must be an object", "authTemplate: []\nauth: [{name: one}]"),
                Map.entry("auth entry must be an object", "auth: [value]"),
                Map.entry("Missing required WFC field: auth.name", "auth: [{fixedHeaders: [{name: X, value: Y}]}]"),
                Map.entry("must define fixedHeaders or loginEndpointAuth", "auth: [{name: empty}]"),
                Map.entry("Duplicate WFC auth name", "auth: [{name: same, fixedHeaders: [{name: X, value: Y}]}, {name: same, fixedHeaders: [{name: Z, value: Y}]}]"),
                Map.entry("exactly one of endpoint or externalEndpointURL", "auth: [{name: login, loginEndpointAuth: {verb: GET, endpoint: /login, externalEndpointURL: https://example.com, expectCookies: true}}]"),
                Map.entry("token handling or expectCookies=true", "auth: [{name: login, loginEndpointAuth: {verb: GET, endpoint: /login}}]"),
                Map.entry("Missing required WFC field: token.sendName", "auth: [{name: login, loginEndpointAuth: {verb: GET, endpoint: /login, token: {extractFrom: header, extractSelector: X, sendIn: header}}}]"),
                Map.entry("configs must be an object", "configs: []\nauth: [{name: one, fixedHeaders: [{name: X, value: Y}]}]"),
                Map.entry("configs.nested must be a string value", "configs: {nested: {value: no}}\nauth: [{name: one, fixedHeaders: [{name: X, value: Y}]}]")
        );

        for (Map.Entry<String, String> invalidDocument : invalidDocuments.entrySet()) {
            File file = config(invalidDocument.getValue());
            Assertions.assertThatThrownBy(() -> loader.load(file))
                    .as(invalidDocument.getKey())
                    .isInstanceOf(CatsException.class)
                    .hasMessageContaining(invalidDocument.getKey());
        }
    }

    @Test
    void shouldWrapFileReadFailures() {
        File missing = tempDir.resolve("missing.yml").toFile();

        Assertions.assertThatThrownBy(() -> loader.load(missing))
                .isInstanceOf(CatsException.class)
                .hasMessageContaining("Unable to read WFC Auth file")
                .hasCauseInstanceOf(IOException.class);
    }

    private File config(String yaml) throws IOException {
        Path file = Files.createTempFile(tempDir, "wfc-auth-", ".yml");
        Files.writeString(file, yaml);
        return file.toFile();
    }
}
