package com.endava.cats.util;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VersionCheckerTest {
    public static WireMockServer wireMockServer;

    private static VersionChecker versionChecker;

    @BeforeAll
    public static void setup() {
        wireMockServer = new WireMockServer(new WireMockConfiguration().dynamicPort());
        wireMockServer.start();
        VersionChecker.baseUrl = "http://localhost:" + wireMockServer.port() + "/latest";
    }

    @BeforeEach
    public void setupEach() {
        versionChecker = new VersionChecker();
    }

    @AfterAll
    public static void clean() {
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
        Assertions.assertThat(result.getVersion()).isEqualTo("not_valid");
        Assertions.assertThat(result.getReleaseNotes()).isNull();
    }
}
