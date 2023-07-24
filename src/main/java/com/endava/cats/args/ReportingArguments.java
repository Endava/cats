package com.endava.cats.args;

import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.github.ludovicianul.prettylogger.config.level.PrettyLevel;
import jakarta.inject.Singleton;
import lombok.Getter;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Singleton
@Getter
public class ReportingArguments {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ReportingArguments.class);
    @CommandLine.Option(names = {"-l", "--log"},
            description = "Set custom log level of a given package(s). You can provide a comma separated list of @|bold,underline package:level|@ pairs or a global log level. This is intended more for debugging purposes", split = ",")
    private List<String> logData;

    @CommandLine.Option(names = {"-g", "--skipLog"},
            description = "A list of log levels to skip. For example you can skip only @|bold,underline note|@ and @|bold,underline info|@ levels, but leave the rest. By default if skips  @|bold,underline note, skip|@ levels which are used to enable more detailed traceability." +
                    " If supplied, this will override the --onlyLog argument", split = ",")
    private List<String> skipLogs;

    @CommandLine.Option(names = {"-O", "--onlyLog"},
            description = "A list of log levels to include. For example you can choose to see only @|bold,underline fav|@ and @|bold,underline warning|@ levels, but leave the rest out", split = ",")
    private List<String> onlyLog;


    @CommandLine.Option(names = {"-D", "--debug"},
            description = "Set CATS log level to ALL. Useful for diagnose when raising bugs")
    private boolean debug;

    @CommandLine.Option(names = {"--printExecutionStatistics"},
            description = "Print a summary of execution times for each endpoint and HTTP method. By default this will print a summary for each endpoint: max, min and average. If you want detailed reports you must supply @|bold --printDetailedExecutionStatistics|@")
    private boolean printExecutionStatistics;

    @CommandLine.Option(names = {"--printDetailedExecutionStatistics"},
            description = "Print detailed execution statistics with execution times for each request")
    private boolean printDetailedExecutionStatistics;

    @CommandLine.Option(names = {"--timestampReports"},
            description = "Output the report inside the @|bold cats-report|@ folder in a sub-folder with the current timestamp")
    private boolean timestampReports;

    @CommandLine.Option(names = {"--reportFormat"},
            description = "The format of the CATS report. Default: @|bold,underline ${DEFAULT-VALUE}|@. You can use @|bold,underline HTML_ONLY|@ if you want the report to not contain any Javascript. This is useful for large number of tests, as the page will render faster and also in CI environments due to Javascript content security policies.")
    private ReportFormat reportFormat = ReportFormat.HTML_JS;

    @CommandLine.Option(names = {"-o", "--output"},
            description = "The output folder of the CATS report. Default: @|bold,underline cats-report|@ in the current directory.")
    private String outputReportFolder = "cats-report";

    @CommandLine.Option(names = {"-j", "--json"},
            description = "Make selected commands output to console in JSON format.")
    private boolean json;

    @CommandLine.Option(names = {"--checkUpdate"},
            description = "If true checks if there is a CATS update available and prints the release notes along with the links. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private boolean checkUpdate = true;

    public List<String> getLogData() {
        return Optional.ofNullable(logData).orElse(Collections.emptyList());
    }

    public static List<PrettyLevel> getAsPrettyLevelList(List<String> logsAsString) {
        return Optional.ofNullable(logsAsString).orElse(Collections.emptyList())
                .stream()
                .filter(entry -> Arrays.stream(PrettyLevel.values())
                        .map(PrettyLevel::name)
                        .anyMatch(level -> level.equalsIgnoreCase(entry)))
                .map(String::toUpperCase)
                .map(PrettyLevel::valueOf)
                .toList();
    }


    public void processLogData() {
        for (String logLine : this.getLogData()) {
            String[] log = logLine.strip().trim().split(":");
            String level;
            String pkg = "com.endava.cats";
            if (log.length == 1) {
                level = log[0];
            } else {
                level = log[1];
                pkg = log[0];
            }
            CatsUtil.setLogLevel(pkg, level);
        }
        if (debug) {
            CatsUtil.setCatsLogLevel("ALL");
            logger.fav("Setting CATS log level to ALL!");
        }
        PrettyLogger.enableLevels(getAsPrettyLevelList(this.onlyLog).toArray(new PrettyLevel[0]));
        PrettyLogger.disableLevels(getAsPrettyLevelList(this.skipLogs).toArray(new PrettyLevel[0]));

        /*if no input is supplied, by default note and skip are not logged*/
        if (this.skipLogs == null && this.onlyLog == null) {
            PrettyLogger.disableLevels(getAsPrettyLevelList(List.of("note", "skip")).toArray(new PrettyLevel[0]));
        }
    }

    public enum ReportFormat {
        HTML_ONLY, HTML_JS, JUNIT
    }

}
