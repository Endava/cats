package com.endava.cats.command;

import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Inspects an OpenAPI contract and scaffolds starter CATS configuration files ({@code headers.yml},
 * {@code refData.yml} pre-populated with unresolved path parameters, and {@code functionalTests.yml}),
 * and/or installs bundled AI agent skills ({@code --skills}) for Devin, Codex, Claude Code, OpenCode, and Cursor.
 */
@CommandLine.Command(
        name = "init",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Scaffold starter CATS configuration files from an OpenAPI contract and/or install CATS AI agent skills (--skills) for Devin, Codex, Claude Code, OpenCode, and Cursor.",
        abbreviateSynopsis = true,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Starter files or AI agent skills generated successfully",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:Contract could not be parsed or files could not be written"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Generate starter configuration files in the current directory:",
                "    cats init -c openapi.yml",
                "", "  Install CATS AI agent skills for all supported coding agents in the current project:",
                "    cats init --skills",
                "", "  Install CATS AI agent skills for specific agents (devin, codex, claude, opencode, cursor):",
                "    cats init --skills devin,claude,cursor",
                "", "  Install CATS AI agent skills globally into your home directory:",
                "    cats init --skills --global"},
        synopsisHeading = "%nUsage: ",
        versionProvider = VersionProvider.class)
@Unremovable
public class InitCommand implements Runnable, CommandLine.IExitCodeGenerator {
    static final List<String> BUNDLED_SKILL_NAMES = List.of(
            "cats",
            "cats-analyze-api",
            "cats-write-tests",
            "cats-fuzz-api",
            "cats-triage-results");

    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();

    @CommandLine.Mixin
    ConfigOptions configOptions;

    @CommandLine.Option(names = {"-c", "--contract"},
            description = "The OpenAPI contract/spec to inspect when scaffolding starter YAML files.")
    String contract;

    @CommandLine.Option(names = {"--skills"},
            arity = "0..1",
            fallbackValue = "all",
            split = ",",
            paramLabel = "<platform>",
            description = "Install CATS AI agent skills for the target platform(s): devin, codex, claude, opencode, cursor, or all (default when no value is given: all).")
    List<String> skillPlatforms;

    @CommandLine.Option(names = {"--global"},
            description = "Install AI agent skills into the user's global home configuration directories instead of the project directory.")
    boolean globalSkills;

    @CommandLine.Option(names = {"-o", "--output"}, defaultValue = ".",
            description = "Directory where starter files or project-scoped skills will be written. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    File outputDir = new File(".");

    @CommandLine.Option(names = {"--force"},
            description = "Overwrite existing configuration or skill files if already present. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    boolean force;

    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output the scaffold summary as JSON.")
    boolean json;

    Path userHomeDir = Paths.get(System.getProperty("user.home", "."));

    @Getter
    private int exitCode = CommandLine.ExitCode.OK;

    @Getter
    private InitResult lastResult;

    public enum AgentPlatform {
        DEVIN("devin", ".devin/skills", ".config/devin/skills"),
        CODEX("codex", ".agents/skills", ".codex/skills"),
        CLAUDE("claude", ".claude/skills", ".claude/skills"),
        OPENCODE("opencode", ".opencode/skills", ".config/opencode/skills"),
        CURSOR("cursor", ".cursor/skills", ".cursor/skills");

        private final String id;
        private final String projectRelativeDir;
        private final String globalHomeRelativeDir;

        AgentPlatform(String id, String projectRelativeDir, String globalHomeRelativeDir) {
            this.id = id;
            this.projectRelativeDir = projectRelativeDir;
            this.globalHomeRelativeDir = globalHomeRelativeDir;
        }

        public String id() {
            return id;
        }

        public Path resolveSkillsRoot(Path projectDir, Path homeDir, boolean global) {
            return global ? homeDir.resolve(globalHomeRelativeDir) : projectDir.resolve(projectRelativeDir);
        }

        public static Set<AgentPlatform> parsePlatforms(List<String> rawValues) {
            if (rawValues == null || rawValues.isEmpty()) {
                return EnumSet.allOf(AgentPlatform.class);
            }
            Set<AgentPlatform> resolved = EnumSet.noneOf(AgentPlatform.class);
            for (String raw : rawValues) {
                if (StringUtils.isBlank(raw)) {
                    continue;
                }
                String norm = raw.trim().toLowerCase(Locale.ROOT);
                switch (norm) {
                    case "all", "*" -> resolved.addAll(EnumSet.allOf(AgentPlatform.class));
                    case "devin" -> resolved.add(DEVIN);
                    case "codex", "agents", "openai" -> resolved.add(CODEX);
                    case "claude", "claude-code", "claudecode" -> resolved.add(CLAUDE);
                    case "opencode", "open-code" -> resolved.add(OPENCODE);
                    case "cursor" -> resolved.add(CURSOR);
                    default -> throw new IllegalArgumentException(
                            "Unsupported skill platform '" + raw + "'. Supported values: devin, codex, claude, opencode, cursor, all");
                }
            }
            return resolved.isEmpty() ? EnumSet.allOf(AgentPlatform.class) : resolved;
        }
    }

    @Override
    public void run() {
        boolean installSkills = skillPlatforms != null && !skillPlatforms.isEmpty();
        boolean hasContract = StringUtils.isNotBlank(contract);
        if (!installSkills && !hasContract) {
            logger.error("Specify either -c <contract> to scaffold starter files, --skills [devin|codex|claude|opencode|cursor|all] to install AI agent skills, or both.");
            exitCode = CommandLine.ExitCode.USAGE;
            return;
        }
        try {
            Path targetDir = outputDir.toPath();
            Files.createDirectories(targetDir);
            List<String> writtenFiles = new ArrayList<>();
            List<String> skippedFiles = new ArrayList<>();
            List<String> securityHeaderNames = new ArrayList<>();
            List<String> unresolvedParams = new ArrayList<>();
            String suggestedCommand = "Invoke /cats, /cats-analyze-api, /cats-write-tests, /cats-fuzz-api, or /cats-triage-results in your AI coding agent";

            if (hasContract) {
                OpenAPI openAPI = OpenApiUtils.readOpenApi(contract);
                Map<String, String> securityHeaders = extractSecurityHeaders(openAPI);
                Map<String, Set<String>> unresolvedByPath = findUnresolvedPathParameters(openAPI);
                String samplePath = firstDocumentedPath(openAPI);
                String sampleMethod = firstDocumentedMethod(openAPI, samplePath);

                writeIfAllowed(targetDir.resolve("headers.yml"), "headers.yml",
                        renderHeadersYaml(securityHeaders), writtenFiles, skippedFiles);
                writeIfAllowed(targetDir.resolve("refData.yml"), "refData.yml",
                        renderRefDataYaml(unresolvedByPath), writtenFiles, skippedFiles);
                writeIfAllowed(targetDir.resolve("functionalTests.yml"), "functionalTests.yml",
                        renderFunctionalTestsYaml(samplePath, sampleMethod), writtenFiles, skippedFiles);

                securityHeaderNames.addAll(securityHeaders.keySet());
                Set<String> allUnresolved = new LinkedHashSet<>();
                unresolvedByPath.values().forEach(allUnresolved::addAll);
                unresolvedParams.addAll(allUnresolved);

                String suggestedServer = extractSuggestedServer(openAPI);
                suggestedCommand = "cats -c %s -s %s --headers %s --refData %s".formatted(
                        contract, suggestedServer,
                        targetDir.resolve("headers.yml"),
                        targetDir.resolve("refData.yml"));
            }

            if (installSkills) {
                Set<AgentPlatform> platforms = AgentPlatform.parsePlatforms(skillPlatforms);
                installAgentSkills(platforms, targetDir, writtenFiles, skippedFiles);
            }

            Path effectiveRoot = (installSkills && !hasContract && globalSkills) ? userHomeDir : targetDir;
            lastResult = new InitResult(
                    hasContract ? contract : "skills:" + String.join(",", skillPlatforms),
                    effectiveRoot.toAbsolutePath().toString(),
                    List.copyOf(securityHeaderNames),
                    List.copyOf(unresolvedParams),
                    List.copyOf(writtenFiles),
                    List.copyOf(skippedFiles),
                    suggestedCommand);
            renderOutput(lastResult, hasContract, installSkills);
            exitCode = CommandLine.ExitCode.OK;
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to initialize CATS configuration: {}", e.getMessage());
            exitCode = CommandLine.ExitCode.SOFTWARE;
        }
    }

    private void installAgentSkills(Set<AgentPlatform> platforms, Path projectDir,
                                    List<String> writtenFiles, List<String> skippedFiles) throws IOException {
        Path baseRoot = globalSkills ? userHomeDir : projectDir;
        for (AgentPlatform platform : platforms) {
            Path skillsRoot = platform.resolveSkillsRoot(projectDir, userHomeDir, globalSkills);
            for (String skillName : BUNDLED_SKILL_NAMES) {
                String content = loadBundledSkillContent(skillName);
                Path skillFile = skillsRoot.resolve(skillName).resolve("SKILL.md");
                Files.createDirectories(skillFile.getParent());
                String displayPath = baseRoot.relativize(skillFile).toString().replace('\\', '/');
                writeIfAllowed(skillFile, displayPath, content, writtenFiles, skippedFiles);
            }
        }
    }

    static String loadBundledSkillContent(String skillName) throws IOException {
        String resourcePath = "skills/" + skillName + "/SKILL.md";
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Bundled skill resource not found: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void writeIfAllowed(Path filePath, String displayName, String content,
                                List<String> written, List<String> skipped) throws IOException {
        if (Files.exists(filePath) && !force) {
            skipped.add(displayName);
            return;
        }
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        written.add(displayName);
    }

    static Map<String, String> extractSecurityHeaders(OpenAPI openAPI) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (openAPI == null || openAPI.getComponents() == null || openAPI.getComponents().getSecuritySchemes() == null) {
            headers.put("Authorization", "Bearer $$API_TOKEN");
            return headers;
        }
        for (Map.Entry<String, SecurityScheme> entry : openAPI.getComponents().getSecuritySchemes().entrySet()) {
            SecurityScheme scheme = entry.getValue();
            if (scheme == null || scheme.getType() == null) {
                continue;
            }
            addSchemeHeader(entry.getKey(), scheme, headers);
        }
        if (headers.isEmpty()) {
            headers.put("Authorization", "Bearer $$API_TOKEN");
        }
        return headers;
    }

    private static void addSchemeHeader(String schemeName, SecurityScheme scheme, Map<String, String> headers) {
        String envName = schemeName.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
        switch (scheme.getType()) {
            case HTTP -> {
                String httpScheme = Optional.ofNullable(scheme.getScheme()).orElse("bearer").toLowerCase(Locale.ROOT);
                if ("basic".equals(httpScheme)) {
                    headers.putIfAbsent("Authorization", "Basic $$" + envName + "_BASIC");
                } else {
                    headers.putIfAbsent("Authorization", "Bearer $$" + envName + "_TOKEN");
                }
            }
            case APIKEY -> addApiKeyHeader(scheme, envName, headers);
            case OAUTH2, OPENIDCONNECT -> headers.putIfAbsent("Authorization", "Bearer $$" + envName + "_TOKEN");
            case MUTUALTLS -> {
                // Mutual TLS is configured via --sslKeystore rather than HTTP headers.
            }
        }
    }

    private static void addApiKeyHeader(SecurityScheme scheme, String envName, Map<String, String> headers) {
        if (scheme.getIn() == SecurityScheme.In.HEADER && StringUtils.isNotBlank(scheme.getName())) {
            headers.putIfAbsent(scheme.getName(), "$$" + envName + "_KEY");
        }
    }

    static Map<String, Set<String>> findUnresolvedPathParameters(OpenAPI openAPI) {
        return OperationDependencyOrder.analyzePathDependencies(openAPI).unresolvedByPath();
    }

    private static String renderHeadersYaml(Map<String, String> securityHeaders) {
        StringBuilder sb = new StringBuilder("# Starter CATS authentication & custom headers configuration\n");
        sb.append("# Secret values prefixed with $$ are read from environment variables and masked in reports.\n");
        sb.append("all:\n");
        securityHeaders.forEach((k, v) -> sb.append("  ").append(k).append(": \"").append(v).append("\"\n"));
        return sb.toString();
    }

    private static String renderRefDataYaml(Map<String, Set<String>> unresolvedByPath) {
        StringBuilder sb = new StringBuilder("# Starter CATS reference data for path/body parameters without a POST producer in the spec\n");
        if (unresolvedByPath.isEmpty()) {
            sb.append("# All path parameters have matching POST producers in the contract.\n");
            sb.append("all:\n");
            sb.append("  # exampleField: \"example-value\"\n");
            return sb.toString();
        }
        unresolvedByPath.forEach((path, params) -> {
            sb.append("\"").append(path).append("\":\n");
            params.forEach(param -> sb.append("  ").append(param).append(": \"<replace-with-valid-").append(param).append(">\"\n"));
        });
        return sb.toString();
    }

    private static String renderFunctionalTestsYaml(String samplePath, String sampleMethod) {
        return """
                # Starter CATS stateful functional flow (run with: cats run -c <spec> -s <url> --functionalFlow functionalTests.yml)
                steps:
                  - name: "Verify happy-path %s on %s"
                    %s: "%s"
                    expect:
                      status: 2XX
                """.formatted(sampleMethod, samplePath, sampleMethod, samplePath);
    }

    private static String firstDocumentedPath(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            return "/example";
        }
        return openAPI.getPaths().keySet().iterator().next();
    }

    private static String firstDocumentedMethod(OpenAPI openAPI, String path) {
        if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().get(path) == null) {
            return HttpMethod.GET.name();
        }
        PathItem item = openAPI.getPaths().get(path);
        if (item.getGet() != null) {
            return HttpMethod.GET.name();
        }
        if (item.getPost() != null) {
            return HttpMethod.POST.name();
        }
        return item.readOperationsMap().keySet().stream().findFirst().map(Enum::name).orElse(HttpMethod.GET.name());
    }

    private static String extractSuggestedServer(OpenAPI openAPI) {
        if (openAPI != null && openAPI.getServers() != null && !openAPI.getServers().isEmpty()) {
            String url = openAPI.getServers().getFirst().getUrl();
            if (StringUtils.isNotBlank(url) && !url.startsWith("/")) {
                return url;
            }
        }
        return "http://localhost:8080";
    }

    private void renderOutput(InitResult result, boolean hasContract, boolean installSkills) {
        if (json) {
            logger.noFormat(JsonUtils.GSON.toJson(result));
            return;
        }
        logger.noFormat("");
        logger.noFormat(AnsiUtils.bold("=== CATS Project Initialized ==="));
        if (hasContract) {
            logger.noFormat("Contract inspected:       {}", result.contract());
            logger.noFormat("Security headers found:   {}", result.securityHeaders());
            logger.noFormat("Unresolved path params:   {}",
                    result.unresolvedPathParameters().isEmpty() ? "none (all covered by POST producers)" : result.unresolvedPathParameters());
        }
        if (installSkills) {
            logger.noFormat("AI agent skills scope:    {}", globalSkills ? "GLOBAL (" + result.outputDirectory() + ")" : "PROJECT (" + result.outputDirectory() + ")");
            logger.noFormat("Installed skill names:    {}", BUNDLED_SKILL_NAMES);
        } else {
            logger.noFormat("Output directory:         {}", result.outputDirectory());
        }
        logger.noFormat("Files generated:          {}", result.writtenFiles());
        if (!result.skippedFiles().isEmpty()) {
            logger.noFormat("Files skipped (existing): {} (use --force to overwrite)", result.skippedFiles());
        }
        logger.noFormat("");
        logger.noFormat("Next step:");
        logger.noFormat("  " + AnsiUtils.boldGreen(result.suggestedCommand()));
    }

    /**
     * Summary of the scaffolded files and contract findings.
     */
    public record InitResult(String contract, String outputDirectory, List<String> securityHeaders,
                             List<String> unresolvedPathParameters, List<String> writtenFiles,
                             List<String> skippedFiles, String suggestedCommand) {
    }
}
