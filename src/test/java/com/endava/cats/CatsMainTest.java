package com.endava.cats;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.command.model.ConfigOptions;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

@QuarkusTest
class CatsMainTest {

    @Inject
    CatsMain catsMain;

    @Inject
    FilterArguments filterArguments;

    @Inject
    ConfigOptions configOptions;

    @TempDir
    Path tempDir;

    @Test
    void shouldPreferExplicitConfigFileOverContractDirectory() throws IOException {
        Path contractFile = tempDir.resolve("openapi.yaml");
        Files.writeString(contractFile, "openapi: 3.0.3");
        Files.writeString(tempDir.resolve(".cats.properties"), "skipLinters=Versions");

        Path explicitFile = tempDir.resolve("custom.properties");
        Files.writeString(explicitFile, "skipLinters=PathPlurals");

        File resolved = catsMain.findConfigFile(new String[]{
                "lint", "-c", contractFile.toString(), "--configFile=" + explicitFile
        });

        Assertions.assertThat(resolved).isEqualTo(explicitFile.toFile());
    }

    @Test
    void shouldDiscoverDotCatsPropertiesInContractDirectory() throws IOException {
        Path contractFile = tempDir.resolve("openapi.yaml");
        Files.writeString(contractFile, "openapi: 3.0.3");
        Path dotProps = tempDir.resolve(".cats.properties");
        Files.writeString(dotProps, "skipLinters=Versions");
        Files.writeString(tempDir.resolve("cats.properties"), "skipLinters=Other");

        File resolved = catsMain.findConfigFile(new String[]{"lint", "-c", contractFile.toString()});

        Assertions.assertThat(resolved).isEqualTo(dotProps.toFile());
    }

    @Test
    void shouldFallbackToCatsPropertiesInContractDirectoryWhenDotFileAbsent() throws IOException {
        Path contractFile = tempDir.resolve("openapi.yaml");
        Files.writeString(contractFile, "openapi: 3.0.3");
        Path props = tempDir.resolve("cats.properties");
        Files.writeString(props, "skipLinters=Versions");

        File resolved = catsMain.findConfigFile(new String[]{"lint", "--contract=" + contractFile});

        Assertions.assertThat(resolved).isEqualTo(props.toFile());
    }

    @Test
    void shouldApplySubcommandPropertiesFromContractDirectoryToLintCommand() throws IOException {
        Path contractFile = tempDir.resolve("petstore.yml");
        Path outputDir = tempDir.resolve("cats-report");
        Path dotProps = tempDir.resolve(".cats.properties");
        Files.copy(Path.of("src/test/resources/petstore-empty.yml"), contractFile);
        Files.writeString(dotProps, "lint.skipLinters=Versions,PathTags\n");

        try {
            int exitCode = catsMain.run("lint", "-c", contractFile.toString(), "-o", outputDir.toString());

            Assertions.assertThat(exitCode).isGreaterThanOrEqualTo(0);
            Assertions.assertThat(filterArguments.getSkipFuzzers()).contains("Versions", "PathTags");
            Assertions.assertThat(configOptions.getResolvedConfigFile())
                    .isEqualTo(dotProps.toAbsolutePath().normalize());
        } finally {
            filterArguments.setSkipFuzzers(Collections.emptyList());
            configOptions.setResolvedConfigFile(null);
        }
    }
}
