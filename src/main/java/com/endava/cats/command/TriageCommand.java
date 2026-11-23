package com.endava.cats.command;

import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.command.triage.ReproducerScriptRenderer;
import com.endava.cats.command.triage.TriageEngine;
import com.endava.cats.command.triage.TriageConsoleRenderer;
import com.endava.cats.command.triage.TriageOptions;
import com.endava.cats.command.triage.TriageReport;
import com.endava.cats.exception.UnsupportedReportException;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Contract-aware triage of the report's problems per occurrence (one problem on one operation), live fix verification ({@code --verify}), {@code --refData} mining from
 * successful {@code 2XX} exchanges, OpenAPI contract-drift patch generation, and {@code --correlations} synthesis.
 */
@CommandLine.Command(
        name = "triage",
        aliases = {"diagnose", "doctor"},
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Triage the problems of a CATS report per operation (root-cause category, diagnosis, remediation, reproducer), verify fixes live against a server, mine working --refData from 2XX exchanges, and generate OpenAPI contract patches and --correlations rules.",
        abbreviateSynopsis = true,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Report triaged (and every server-error occurrence FIXED when --verify is enabled)",
                "@|bold 2|@:Usage error: wrong input, or a report folder without problems written by this CATS version",
                "@|bold 1|@:Execution error, or a server-error (5XX) problem occurrence that is not FIXED (still failing or inconclusive) during --verify"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Triage the error problems of the default cats-report folder:",
                "    cats triage",
                "", "  Triage both error and warning problems, as JSON:",
                "    cats triage --include-warnings -j",
                "", "  Replay 1 representative test per problem occurrence against a live server to verify fixes:",
                "    cats triage -r cats-report --verify -s http://localhost:8080",
                "", "  Mine a ready-to-use --refData file from 2XX exchanges and synthesize --correlations rules:",
                "    cats triage -r cats-report -c openapi.yml --export-refdata refData.yml --export-correlations correlations.yml",
                "", "  Generate an OpenAPI contract patch for undocumented status codes observed during the run:",
                "    cats triage -r cats-report -c openapi.yml --export-contract-patch contract-patch.yml"},
        synopsisHeading = "%nUsage: ",
        versionProvider = VersionProvider.class)
@Unremovable
public class TriageCommand implements Runnable, CommandLine.IExitCodeGenerator {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();

    @CommandLine.Mixin
    ConfigOptions configOptions;

    @CommandLine.Option(names = {"-r", "--report"}, defaultValue = "cats-report",
            description = "The CATS report folder containing Test*.json files (default: ${DEFAULT-VALUE}).")
    String reportFolder = "cats-report";

    @CommandLine.Option(names = {"-c", "--contract"},
            description = "Optional OpenAPI contract used to detect undocumented status codes, validate synthesized field names and type placeholder values.")
    String contractPath;

    @CommandLine.Option(names = {"-s", "--server"},
            description = "Optional target server base URL override when running --verify.")
    String serverUrl;

    @CommandLine.Option(names = {"-H", "--header"},
            description = "Optional header(s) (name=value) to override when replaying problem occurrences with --verify.")
    Map<String, String> customHeaders = new LinkedHashMap<>();

    @CommandLine.Option(names = {"--verify"},
            description = "Replay 1 representative request per problem occurrence against the server and report FIXED, STILL_FAILING or INCONCLUSIVE.")
    boolean verify;

    @CommandLine.Option(names = {"--show-reproducers"},
            description = "Include full curl commands in console output. JSON and --export-reproducers always retain them.")
    boolean showReproducers;

    @CommandLine.Option(names = {"-w", "--include-warnings"},
            description = "Include warning test cases in triage. By default only errors are triaged.")
    boolean includeWarnings;

    @CommandLine.Option(names = {"--export-correlations"},
            description = "Write synthesized --correlations YAML rules inferred from 4XX validation errors to the given file.")
    String exportCorrelationsFile;

    @CommandLine.Option(names = {"--export-refdata"},
            description = "Mine working path parameters and resource IDs from 2XX exchanges in the report and write a ready-to-use --refData YAML file.")
    String exportRefDataFile;

    @CommandLine.Option(names = {"--export-contract-patch"},
            description = "Write an OpenAPI paths overlay YAML containing undocumented status codes observed during the run.")
    String exportContractPatchFile;

    @CommandLine.Option(names = {"--export-reproducers"},
            description = "Write a shell script with one minimal curl reproducer per problem occurrence.")
    String exportReproducersFile;

    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output the triage report as JSON.")
    boolean json;

    UnaryOperator<String> environment = System::getenv;

    @Getter
    private int exitCode = CommandLine.ExitCode.OK;

    @Getter
    private TriageReport lastReport;

    @Override
    public void run() {
        try {
            Path folder = Paths.get(reportFolder);
            if (!Files.isDirectory(folder)) {
                throw new IllegalArgumentException("Report directory does not exist: " + folder);
            }
            lastReport = new TriageEngine(triageOptions()).triage(folder, loadOpenApiIfSupplied());
            writeExports(lastReport);
            renderReport(lastReport);
            exitCode = verify && TriageEngine.hasUnfixedServerCrash(lastReport) ? CommandLine.ExitCode.SOFTWARE : CommandLine.ExitCode.OK;
        } catch (UnsupportedReportException e) {
            logger.error("Cannot triage CATS report: {}", e.getMessage());
            exitCode = CommandLine.ExitCode.USAGE;
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to triage CATS report: {}", e.getMessage());
            exitCode = CommandLine.ExitCode.SOFTWARE;
        }
    }

    private TriageOptions triageOptions() {
        return new TriageOptions(includeWarnings, verify, serverUrl, customHeaders, environment);
    }

    private OpenAPI loadOpenApiIfSupplied() {
        if (StringUtils.isBlank(contractPath)) {
            return null;
        }
        try {
            return OpenApiUtils.readOpenApi(contractPath);
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not read contract {}: {}", contractPath, e.getMessage());
            return null;
        }
    }

    private void writeExports(TriageReport report) throws IOException {
        writeIfRequested(exportCorrelationsFile, report.synthesizedCorrelationsYaml());
        writeIfRequested(exportRefDataFile, report.minedRefDataYaml());
        writeIfRequested(exportContractPatchFile, report.contractPatchYaml());
        writeIfRequested(exportReproducersFile, ReproducerScriptRenderer.render(report));
    }

    private static void writeIfRequested(String file, String content) throws IOException {
        if (StringUtils.isNotBlank(file)) {
            Files.writeString(Paths.get(file), content, StandardCharsets.UTF_8);
        }
    }

    private void renderReport(TriageReport report) {
        if (json) {
            logger.noFormat(JsonUtils.GSON.toJson(report));
            return;
        }
        TriageConsoleRenderer.render(logger, report, showReproducers);
    }

}
