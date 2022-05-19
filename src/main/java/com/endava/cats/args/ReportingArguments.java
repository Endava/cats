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
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(ReportingArguments.class);
    private static final String EMPTY = "empty";

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

    public List<String> getLogData() {
        return Optional.ofNullable(logData).orElse(Collections.emptyList());
    }

    public void processLogData() {
        for (String logLine : this.getLogData()) {
            String[] log = logLine.strip().trim().split(":");
            CatsUtil.setLogLevel(log[0], log[1]);
            LOGGER.info("Setting log level to {} for package {}", log[1], log[0]);
        }
        if (debug) {
            CatsUtil.setCatsLogLevel("ALL");
            LOGGER.fav("Setting CATS log level to ALL!");
        }
    }

    public enum ReportFormat {
        HTML_ONLY, HTML_JS, JUNIT
    }

}
