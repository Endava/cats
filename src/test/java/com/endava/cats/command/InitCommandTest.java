package com.endava.cats.command;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
        Assertions.assertThat(Files.readString(tempDir.resolve("functionalTests.yml"))).contains("steps:");

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
    void shouldInstallSkillsForAllPlatformsInProjectDirectory(@TempDir Path tempDir) throws Exception {
        InitCommand initCommand = new InitCommand();
        initCommand.skillPlatforms = List.of("all");
        initCommand.outputDir = tempDir.toFile();

        initCommand.run();

        Assertions.assertThat(initCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
        Assertions.assertThat(initCommand.getLastResult().writtenFiles()).hasSize(25);
        Assertions.assertThat(tempDir.resolve(".devin/skills/cats/SKILL.md")).exists();
        Assertions.assertThat(tempDir.resolve(".agents/skills/cats-analyze-api/SKILL.md")).exists();
        Assertions.assertThat(tempDir.resolve(".claude/skills/cats-write-tests/SKILL.md")).exists();
        Assertions.assertThat(tempDir.resolve(".opencode/skills/cats-fuzz-api/SKILL.md")).exists();
        Assertions.assertThat(tempDir.resolve(".cursor/skills/cats-triage-results/SKILL.md")).exists();
        Assertions.assertThat(Files.readString(tempDir.resolve(".claude/skills/cats-write-tests/SKILL.md")))
                .contains("--functionalFlow")
                .doesNotContain("functionalFuzzerFile");
    }

    @Test
    void shouldInstallSkillsForSelectedPlatformsGlobally(@TempDir Path tempHome) {
        InitCommand initCommand = new InitCommand();
        initCommand.skillPlatforms = List.of("devin", "claude", "opencode");
        initCommand.globalSkills = true;
        initCommand.userHomeDir = tempHome;

        initCommand.run();

        Assertions.assertThat(initCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
        Assertions.assertThat(initCommand.getLastResult().writtenFiles()).hasSize(15);
        Assertions.assertThat(tempHome.resolve(".config/devin/skills/cats/SKILL.md")).exists();
        Assertions.assertThat(tempHome.resolve(".claude/skills/cats-fuzz-api/SKILL.md")).exists();
        Assertions.assertThat(tempHome.resolve(".config/opencode/skills/cats-triage-results/SKILL.md")).exists();
        Assertions.assertThat(tempHome.resolve(".cursor/skills/cats/SKILL.md")).doesNotExist();

        // Second run without --force skips existing global skill files
        initCommand.run();
        Assertions.assertThat(initCommand.getLastResult().writtenFiles()).isEmpty();
        Assertions.assertThat(initCommand.getLastResult().skippedFiles()).hasSize(15);
    }

    @Test
    void shouldReturnUsageExitCodeWhenNeitherContractNorSkillsProvided(@TempDir Path tempDir) {
        InitCommand initCommand = new InitCommand();
        initCommand.outputDir = tempDir.toFile();

        initCommand.run();

        Assertions.assertThat(initCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
    }

    @Test
    void shouldReturnSoftwareExitCodeWhenContractIsMissing(@TempDir Path tempDir) {
        InitCommand initCommand = new InitCommand();
        initCommand.contract = "src/test/resources/non_existent_spec.yml";
        initCommand.outputDir = tempDir.toFile();

        initCommand.run();

        Assertions.assertThat(initCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    }

    @Test
    void shouldExtractAllSecuritySchemeTypesAndHandlePlatformAliases() {
        OpenAPI api = new OpenAPI();
        Assertions.assertThat(InitCommand.extractSecurityHeaders(api)).containsEntry("Authorization", "Bearer $$API_TOKEN");

        Components components = new Components()
                .addSecuritySchemes("basicAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("basic"))
                .addSecuritySchemes("headerKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER).name("X-API-Key"))
                .addSecuritySchemes("queryKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.QUERY).name("api_key"))
                .addSecuritySchemes("oauth", new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2))
                .addSecuritySchemes("mtls", new SecurityScheme()
                        .type(SecurityScheme.Type.MUTUALTLS));
        api.setComponents(components);

        Map<String, String> headers = InitCommand.extractSecurityHeaders(api);
        Assertions.assertThat(headers).containsKeys("Authorization", "X-API-Key");

        Assertions.assertThat(InitCommand.AgentPlatform.parsePlatforms(List.of("codex", "cursor")))
                .containsExactlyInAnyOrder(InitCommand.AgentPlatform.CODEX, InitCommand.AgentPlatform.CURSOR);
        Assertions.assertThat(InitCommand.AgentPlatform.parsePlatforms(null)).hasSize(5);
        Assertions.assertThatThrownBy(() -> InitCommand.AgentPlatform.parsePlatforms(List.of("unknown-agent")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
