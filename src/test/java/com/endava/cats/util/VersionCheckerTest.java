package com.endava.cats.util;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@QuarkusTest
class VersionCheckerTest {
    public static WireMockServer wireMockServer;

    private static VersionChecker versionChecker;

    @BeforeAll
    static void setup() {
        wireMockServer = new WireMockServer(new WireMockConfiguration().dynamicPort());
        wireMockServer.start();
        VersionChecker.baseUrl = "http://localhost:" + wireMockServer.port() + "/latest";
    }

    @BeforeEach
    void setupEach() {
        versionChecker = new VersionChecker();
    }

    @AfterAll
    static void clean() {
        wireMockServer.stop();
    }

    @Test
    void shouldNotReturnNewVersion() {
        wireMockServer.stubFor(WireMock.get("/latest").willReturn(WireMock.ok("""
                {
                    "tag_name": "cats-8.0.0",
                    "body": "release notes"
                }
                """)));
        VersionChecker.CheckResult result = versionChecker.checkForNewVersion("8.7.7");

        Assertions.assertThat(result.isNewVersion()).isFalse();
        Assertions.assertThat(result.getVersion()).isEqualTo("8.0.0");

    }

    @Test
    void shouldReturnNewVersion() {
        wireMockServer.stubFor(WireMock.get("/latest").willReturn(WireMock.ok("""
                {
                    "tag_name": "cats-8.9.9",
                    "body": "release notes"
                }
                """)));
        VersionChecker.CheckResult result = versionChecker.checkForNewVersion("8.7.7");

        Assertions.assertThat(result.isNewVersion()).isTrue();
        Assertions.assertThat(result.getReleaseNotes()).isEqualTo("release notes");
        Assertions.assertThat(result.getVersion()).isEqualTo("8.9.9");
    }

    @Test
    void shouldNotReturnNewVersionWhenException() {
        wireMockServer.stubFor(WireMock.get("/latest").willReturn(WireMock.ok("""
                {
                    "tag_name": "not_valid",
                    "body": "release notes"
                }
                """)));
        VersionChecker.CheckResult result = versionChecker.checkForNewVersion("8.7.7");

        Assertions.assertThat(result.isNewVersion()).isFalse();
        Assertions.assertThat(result.getVersion()).isNull();
        Assertions.assertThat(result.getReleaseNotes()).isNull();
    }

    @Test
    void shouldIgnoreNonSuccessfulResponses() {
        wireMockServer.stubFor(WireMock.get("/latest").willReturn(WireMock.aResponse().withStatus(503).withBody("""
                {"tag_name":"cats-9.0.0","body":"release notes"}
                """)));

        VersionChecker.CheckResult result = versionChecker.checkForNewVersion("8.7.7");

        Assertions.assertThat(result.isNewVersion()).isFalse();
        Assertions.assertThat(result.getVersion()).isNull();
    }

    @Test
    void shouldIgnoreOversizedResponses() {
        String body = "{\"tag_name\":\"cats-9.0.0\",\"body\":\"" + "x".repeat(300000) + "\"}";
        wireMockServer.stubFor(WireMock.get("/latest").willReturn(WireMock.ok(body)));

        VersionChecker.CheckResult result = versionChecker.checkForNewVersion("8.7.7");

        Assertions.assertThat(result.isNewVersion()).isFalse();
        Assertions.assertThat(result.getVersion()).isNull();
        Assertions.assertThat(result.getReleaseNotes()).isNull();
    }

    @Test
    void shouldUseShortUpdateCheckTimeouts() {
        OkHttpClient httpClient = (OkHttpClient) ReflectionTestUtils.getField(versionChecker, "httpClient");

        Assertions.assertThat(httpClient).isNotNull();
        Assertions.assertThat(httpClient.callTimeoutMillis()).isEqualTo(5000);
        Assertions.assertThat(httpClient.connectTimeoutMillis()).isEqualTo(2000);
        Assertions.assertThat(httpClient.readTimeoutMillis()).isEqualTo(2000);
    }

    @Test
    void shouldBeSameVersion() {
        String version1 = "1.1.1";
        String version2 = "1.1.1";

        int result = VersionChecker.compare(version1, version2);
        Assertions.assertThat(result).isZero();
    }
}
