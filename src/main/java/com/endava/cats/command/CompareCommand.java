package com.endava.cats.command;

import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.report.BaselineComparisonEngine;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import lombok.Getter;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;

/**
 * Compares two CATS report directories or {@code cats-summary-report.json} files apples-to-apples across their
 * shared {@code (contractPath, httpMethod, fuzzer)} scope.
 */
@CommandLine.Command(
        name = "compare",
        aliases = {"diff"},
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Compare two cats-report folders or cats-summary-report.json files apples-to-apples for regressions and fixes.",
        abbreviateSynopsis = true,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:No new regressions detected compared to baseline",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:New error regressions (or warnings with --failOnNewWarnings) detected, or report files could not be read"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Compare a baseline report folder against a PR report folder:",
                "    cats compare -b baseline-report -t pr-report",
                "", "  Compare two reports and output JSON, failing on new warnings as well:",
                "    cats compare -b baseline/cats-summary-report.json -t current/cats-summary-report.json --failOnNewWarnings -j"},
        synopsisHeading = "%nUsage: ",
        versionProvider = VersionProvider.class)
@Unremovable
public class CompareCommand implements Runnable, CommandLine.IExitCodeGenerator {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();

    @CommandLine.Mixin
    ConfigOptions configOptions;

    @CommandLine.Option(names = {"-b", "--baseline"}, required = true,
            description = "Baseline cats-report folder or cats-summary-report.json file.")
    File baseline;

    @CommandLine.Option(names = {"-t", "--target"}, required = true,
            description = "Target (current/PR) cats-report folder or cats-summary-report.json file.")
    File target;

    @CommandLine.Option(names = {"--failOnNewErrors"}, defaultValue = "true", fallbackValue = "true",
            description = "Exit with code 1 when new error regressions are introduced. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    boolean failOnNewErrors = true;

    @CommandLine.Option(names = {"--failOnNewWarnings"},
            description = "Also exit with code 1 when new warnings are introduced. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    boolean failOnNewWarnings;

    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output the comparison result as JSON.")
    boolean json;

    @Getter
    private int exitCode = CommandLine.ExitCode.OK;

    @Getter
    private BaselineComparisonEngine.ComparisonResult lastResult;

    @Override
    public void run() {
        try {
            lastResult = BaselineComparisonEngine.compareReports(baseline.toPath(), target.toPath());
            if (json) {
                logger.noFormat(JsonUtils.GSON.toJson(lastResult));
            } else {
                BaselineComparisonEngine.renderToConsole(logger, lastResult, failOnNewWarnings);
            }
            boolean regressed = (failOnNewErrors && lastResult.newErrorCount() > 0) ||
                    (failOnNewWarnings && lastResult.newWarningCount() > 0);
            exitCode = regressed ? CommandLine.ExitCode.SOFTWARE : CommandLine.ExitCode.OK;
        } catch (IOException e) {
            logger.error("Failed to compare reports: {}", e.getMessage());
            exitCode = CommandLine.ExitCode.SOFTWARE;
        }
    }
}
