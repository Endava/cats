package com.endava.cats.auth.wfc;

import com.endava.cats.exception.CatsException;
import com.webfuzzing.commons.auth.Auth;
import com.webfuzzing.commons.auth.AuthenticationInfo;
import com.webfuzzing.commons.auth.Header;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

@QuarkusTest
class WfcAuthConfigLoaderTest {
    private final WfcAuthConfigLoader loader = new WfcAuthConfigLoader();

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
}
