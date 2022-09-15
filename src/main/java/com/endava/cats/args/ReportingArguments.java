package com.endava.cats.args;

import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Getter;
import picocli.CommandLine;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Singleton
@Getter
public class ReportingArguments {
    private static final String EMPTY = "empty";
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ReportingArguments.class);
    @CommandLine.Option(names = {"-l", "--log"},
            description = "Set custom log level of a given package(s). You can provide a comma separated list of @|bold,underline package:level|@ pairs", split = ",")
    private List<String> logData;

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


    public List<String> getLogData() {
        return Optional.ofNullable(logData).orElse(Collections.emptyList());
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
    }

    public enum ReportFormat {
        HTML_ONLY, HTML_JS, JUNIT
    }

}
