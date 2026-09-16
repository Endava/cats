package com.endava.cats.args;

import com.endava.cats.exception.CatsException;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

@QuarkusTest
class AuthArgumentsTest {

    @Test
    void shouldUseStrictTlsByDefault() {
        Assertions.assertThat(new AuthArguments().isInsecure()).isFalse();
    }

    @Test
    void shouldLoadEnvironmentFile(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve("cats.env");
        Files.writeString(envFile, "# ignored\nTOKEN=from-file\nexport QUOTED='quoted value'\nWITH_EQUALS=a=b\n");
        AuthArguments args = new AuthArguments();
        new picocli.CommandLine(args).parseArgs("--envFile", envFile.toString());

        Assertions.assertThat(args.getEnvironmentVariables()).containsEntry("TOKEN", "from-file")
                .containsEntry("QUOTED", "quoted value").containsEntry("WITH_EQUALS", "a=b");
        Assertions.assertThat(args.getDynamicVariablesContext()).containsEntry("TOKEN", "from-file");
    }

    @Test
    void shouldAutomaticallyLoadEnvironmentFileFromCurrentDirectory(@TempDir Path tempDir) throws Exception {
        Path defaultEnvFile = tempDir.resolve(".env").toAbsolutePath();
        Files.writeString(defaultEnvFile, "TOKEN=auto-detected\n");
        AuthArguments args = new AuthArguments() {
            @Override
            Path defaultEnvironmentFile() {
                return defaultEnvFile;
            }
        };

        Assertions.assertThat(args.getEnvironmentVariables()).containsEntry("TOKEN", "auto-detected");
        Assertions.assertThat(args.getEnvironmentFileStatus()).isEqualTo(defaultEnvFile + " (auto-detected)");
    }

    @Test
    void shouldPreferExplicitEnvironmentFile(@TempDir Path tempDir) throws Exception {
        Path defaultEnvFile = tempDir.resolve(".env").toAbsolutePath();
        Path explicitEnvFile = tempDir.resolve("explicit.env").toAbsolutePath();
        Files.writeString(defaultEnvFile, "TOKEN=default\n");
        Files.writeString(explicitEnvFile, "TOKEN=explicit\n");
        AuthArguments args = new AuthArguments() {
            @Override
            Path defaultEnvironmentFile() {
                return defaultEnvFile;
            }
        };
        new picocli.CommandLine(args).parseArgs("--envFile", explicitEnvFile.toString());

        Assertions.assertThat(args.getEnvironmentVariables()).containsEntry("TOKEN", "explicit");
        Assertions.assertThat(args.getEnvironmentFileStatus()).isEqualTo(explicitEnvFile.toString());
    }

    @Test
    void shouldDisableAutomaticEnvironmentFileLoading(@TempDir Path tempDir) throws Exception {
        Path defaultEnvFile = tempDir.resolve(".env").toAbsolutePath();
        Files.writeString(defaultEnvFile, "TOKEN=default\n");
        AuthArguments args = new AuthArguments() {
            @Override
            Path defaultEnvironmentFile() {
                return defaultEnvFile;
            }
        };
        new picocli.CommandLine(args).parseArgs("--noEnvFile");

        Assertions.assertThat(args.getEnvironmentVariables()).isEmpty();
        Assertions.assertThat(args.getEnvironmentFileStatus()).isEqualTo("disabled");
    }

    @Test
    void shouldRejectConflictingEnvironmentFileOptions(@TempDir Path tempDir) {
        AuthArguments args = new AuthArguments();
        new picocli.CommandLine(args).parseArgs("--envFile", tempDir.resolve("custom.env").toString(), "--noEnvFile");

        Assertions.assertThatThrownBy(args::getEnvironmentVariables).isInstanceOf(CatsException.class)
                .hasMessageContaining("--envFile", "--noEnvFile");
    }

    @Test
    void shouldReturnEmptyBasicAuth() {
        AuthArguments args = new AuthArguments();
        Assertions.assertThat(args.isBasicAuthSupplied()).isFalse();
    }


    @Test
    void shouldReturnBasicAuthHeader() {
        AuthArguments args = new AuthArguments();
        ReflectionTestUtils.setField(args, "basicAuth", "user:pwd");
        Assertions.assertThat(args.isBasicAuthSupplied()).isTrue();
        Assertions.assertThat(args.getBasicAuthHeader()).isEqualTo("Basic dXNlcjpwd2Q=");

    }

    @Test
    void shouldReturnFalseMutualTls() {
        AuthArguments args = new AuthArguments();
        Assertions.assertThat(args.isMutualTls()).isFalse();
    }

    @Test
    void shouldReturnTrueMutualTls() {
        AuthArguments args = new AuthArguments();
        ReflectionTestUtils.setField(args, "sslKeystore", "keystore.jks");
        Assertions.assertThat(args.isMutualTls()).isTrue();
    }


    @ParameterizedTest
    @CsvSource(value = {"null,0", "localhost,0", "null,8080"}, nullValues = "null")
    void shouldReturnNotIsProxyHost(String host, int port) {
        AuthArguments args = new AuthArguments();

        ReflectionTestUtils.setField(args, "proxyHost", host);
        ReflectionTestUtils.setField(args, "proxyPort", port);

        Assertions.assertThat(args.isProxySupplied()).isFalse();
        Assertions.assertThat(args.getProxy().type()).isEqualTo(Proxy.Type.DIRECT);
    }


    @Test
    void shouldReturnProxy() {
        AuthArguments args = new AuthArguments();
        ReflectionTestUtils.setField(args, "proxyHost", "localhost");
        ReflectionTestUtils.setField(args, "proxyPort", 8080);

        Assertions.assertThat(args.isProxySupplied()).isTrue();
        Assertions.assertThat(args.getProxy().type()).isEqualTo(Proxy.Type.HTTP);
    }
}
