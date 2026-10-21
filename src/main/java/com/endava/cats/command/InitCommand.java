package com.endava.cats.command;

import com.endava.cats.command.model.ConfigOptions;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Inspects an OpenAPI contract and scaffolds starter CATS configuration files ({@code headers.yml},
 * {@code refData.yml} pre-populated with unresolved path parameters, and {@code functionalTests.yml}).
 */
@CommandLine.Command(
        name = "init",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Inspect an OpenAPI contract and generate starter headers.yml, refData.yml, and functionalTests.yml files.",
        abbreviateSynopsis = true,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Starter files generated successfully",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:Contract could not be parsed or starter files could not be written"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Generate starter configuration files in the current directory:",
                "    cats init -c openapi.yml",
                "", "  Generate starter configuration files in a target folder, overwriting existing files:",
                "    cats init -c openapi.yml -o ./cats-config --force"},
        synopsisHeading = "%nUsage: ",
        versionProvider = VersionProvider.class)
@Unremovable
public class InitCommand implements Runnable, CommandLine.IExitCodeGenerator {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();

    @CommandLine.Mixin
    ConfigOptions configOptions;

    @CommandLine.Option(names = {"-c", "--contract"}, required = true,
            description = "The OpenAPI contract/spec to inspect.")
    String contract;

    @CommandLine.Option(names = {"-o", "--output"}, defaultValue = ".",
            description = "Directory where starter files will be written. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    File outputDir = new File(".");

    @CommandLine.Option(names = {"--force"},
            description = "Overwrite existing configuration files if already present. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    boolean force;

    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output the scaffold summary as JSON.")
    boolean json;

    @Getter
    private int exitCode = CommandLine.ExitCode.OK;

    @Getter
    private InitResult lastResult;

    @Override
    public void run() {
        try {
            OpenAPI openAPI = OpenApiUtils.readOpenApi(contract);
            Path targetDir = outputDir.toPath();
            Files.createDirectories(targetDir);

            Map<String, String> securityHeaders = extractSecurityHeaders(openAPI);
            Map<String, Set<String>> unresolvedByPath = findUnresolvedPathParameters(openAPI);
            String samplePath = firstDocumentedPath(openAPI);
            String sampleMethod = firstDocumentedMethod(openAPI, samplePath);

            List<String> writtenFiles = new ArrayList<>();
            List<String> skippedFiles = new ArrayList<>();

            writeIfAllowed(targetDir.resolve("headers.yml"), renderHeadersYaml(securityHeaders), writtenFiles, skippedFiles);
            writeIfAllowed(targetDir.resolve("refData.yml"), renderRefDataYaml(unresolvedByPath), writtenFiles, skippedFiles);
            writeIfAllowed(targetDir.resolve("functionalTests.yml"),
                    renderFunctionalTestsYaml(samplePath, sampleMethod), writtenFiles, skippedFiles);

            Set<String> allUnresolvedParams = new LinkedHashSet<>();
            unresolvedByPath.values().forEach(allUnresolvedParams::addAll);

            String suggestedServer = extractSuggestedServer(openAPI);
            lastResult = new InitResult(
                    contract,
                    targetDir.toAbsolutePath().toString(),
                    List.copyOf(securityHeaders.keySet()),
                    List.copyOf(allUnresolvedParams),
                    List.copyOf(writtenFiles),
                    List.copyOf(skippedFiles),
                    "cats -c %s -s %s --headers %s --refData %s".formatted(
                            contract, suggestedServer,
                            targetDir.resolve("headers.yml"),
                            targetDir.resolve("refData.yml")));
            renderOutput(lastResult);
            exitCode = CommandLine.ExitCode.OK;
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to initialize CATS configuration: {}", e.getMessage());
            exitCode = CommandLine.ExitCode.SOFTWARE;
        }
    }

    private void writeIfAllowed(Path filePath, String content, List<String> written, List<String> skipped) throws IOException {
        if (Files.exists(filePath) && !force) {
            skipped.add(filePath.getFileName().toString());
            return;
        }
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        written.add(filePath.getFileName().toString());
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
            case APIKEY -> {
                if (scheme.getIn() == SecurityScheme.In.HEADER && StringUtils.isNotBlank(scheme.getName())) {
                    headers.putIfAbsent(scheme.getName(), "$$" + envName + "_KEY");
                }
            }
            case OAUTH2, OPENIDCONNECT -> headers.putIfAbsent("Authorization", "Bearer $$" + envName + "_TOKEN");
            case MUTUALTLS -> {
                // Mutual TLS is configured via --sslKeystore rather than HTTP headers.
            }
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
                # Starter CATS custom functional tests
                "%s":
                  description: "Verify happy-path %s on %s"
                  httpMethod: %s
                  expectedResponseCode: 2XX
                """.formatted(samplePath, sampleMethod, samplePath, sampleMethod);
    }

    private static String firstDocumentedPath(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            return "/example";
        }
        return openAPI.getPaths().keySet().iterator().next();
    }

    private static String firstDocumentedMethod(OpenAPI openAPI, String path) {
        if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().get(path) == null) {
            return "GET";
        }
        PathItem item = openAPI.getPaths().get(path);
        if (item.getGet() != null) {
            return "GET";
        }
        if (item.getPost() != null) {
            return "POST";
        }
        return item.readOperationsMap().keySet().stream().findFirst().map(Enum::name).orElse("GET");
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

    private void renderOutput(InitResult result) {
        if (json) {
            logger.noFormat(JsonUtils.GSON.toJson(result));
            return;
        }
        logger.noFormat("");
        logger.noFormat(AnsiUtils.bold("=== CATS Project Initialized ==="));
        logger.noFormat("Contract inspected:       {}", result.contract());
        logger.noFormat("Output directory:         {}", result.outputDirectory());
        logger.noFormat("Security headers found:   {}", result.securityHeaders());
        logger.noFormat("Unresolved path params:   {}",
                result.unresolvedPathParameters().isEmpty() ? "none (all covered by POST producers)" : result.unresolvedPathParameters());
        logger.noFormat("Files generated:          {}", result.writtenFiles());
        if (!result.skippedFiles().isEmpty()) {
            logger.noFormat("Files skipped (existing): {} (use --force to overwrite)", result.skippedFiles());
        }
        logger.noFormat("");
        logger.noFormat("Next step — run CATS with the generated configuration:");
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
