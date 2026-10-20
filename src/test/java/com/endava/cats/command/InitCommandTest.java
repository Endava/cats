package com.endava.cats.command;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

@QuarkusTest
class InitCommandTest {

    @Test
    void shouldGenerateStarterConfigFilesAndPreserveExistingUnlessForceSet(@TempDir Path tempDir) throws Exception {
        InitCommand initCommand = new InitCommand();
        initCommand.contract = "src/test/resources/petstore.yml";
        initCommand.outputDir = tempDir.toFile();

        initCommand.run();

        Assertions.assertThat(initCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
        Assertions.assertThat(tempDir.resolve("headers.yml")).exists();
        Assertions.assertThat(tempDir.resolve("refData.yml")).exists();
        Assertions.assertThat(tempDir.resolve("functionalTests.yml")).exists();
        Assertions.assertThat(initCommand.getLastResult().writtenFiles())
                .containsExactly("headers.yml", "refData.yml", "functionalTests.yml");
        Assertions.assertThat(Files.readString(tempDir.resolve("headers.yml"))).contains("all:");
        Assertions.assertThat(Files.readString(tempDir.resolve("refData.yml"))).contains("hook_id");

        // Second run without --force should skip existing files
        initCommand.json = true;
        initCommand.run();
        Assertions.assertThat(initCommand.getLastResult().writtenFiles()).isEmpty();
        Assertions.assertThat(initCommand.getLastResult().skippedFiles())
                .containsExactly("headers.yml", "refData.yml", "functionalTests.yml");

        // Run with --force overwrites existing files
        initCommand.force = true;
        initCommand.run();
        Assertions.assertThat(initCommand.getLastResult().writtenFiles())
                .containsExactly("headers.yml", "refData.yml", "functionalTests.yml");
    }

    @Test
    void shouldReturnSoftwareExitCodeWhenContractIsMissing(@TempDir Path tempDir) {
        InitCommand initCommand = new InitCommand();
        initCommand.contract = "src/test/resources/non_existent_spec.yml";
        initCommand.outputDir = tempDir.toFile();

        initCommand.run();

        Assertions.assertThat(initCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    }
}
