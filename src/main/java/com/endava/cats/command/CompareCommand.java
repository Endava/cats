package com.endava.cats.command;

import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.command.model.ComparisonResult;
import com.endava.cats.exception.UnsupportedReportException;
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
 * Compares the persisted problem occurrences of two CATS summaries.
 */
@CommandLine.Command(
        name = "compare",
        aliases = {"diff"},
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Compare problem occurrences in two cats-report folders or cats-summary-report.json files.",
        abbreviateSynopsis = true,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:No new regressions detected compared to baseline",
                "@|bold 2|@:Usage error: missing, invalid or incompatible report",
                "@|bold 1|@:New error occurrences (or warnings with --failOnNewWarnings)"},
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

    @CommandLine.Option(names = {"--failOnNewWarnings"},
            description = "Also exit with code 1 when new warnings are introduced. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    boolean failOnNewWarnings;

    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output the comparison result as JSON.")
    boolean json;

    @Getter
    private int exitCode = CommandLine.ExitCode.OK;

    @Getter
    private ComparisonResult lastResult;

    @Override
    public void run() {
        exitCode = CommandLine.ExitCode.OK;
        lastResult = null;
        try {
            compareReports();
            exitCode = lastResult.regression() ? CommandLine.ExitCode.SOFTWARE : CommandLine.ExitCode.OK;
        } catch (IOException | UnsupportedReportException e) {
            logger.error("Failed to compare reports: {}", e.getMessage());
            exitCode = CommandLine.ExitCode.USAGE;
        }
    }

    private void compareReports() throws IOException {
        var reports = new BaselineComparisonEngine.Reports(baseline.toString(), target.toString(),
                BaselineComparisonEngine.loadReport(baseline.toPath()), BaselineComparisonEngine.loadReport(target.toPath()));
        lastResult = BaselineComparisonEngine.compare(reports, failOnNewWarnings);
        if (json) {
            logger.noFormat(JsonUtils.GSON.toJson(lastResult));
        } else {
            BaselineComparisonEngine.renderToConsole(logger, lastResult, reports);
        }
    }
}
