package com.endava.cats.args;

import lombok.Getter;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@Getter
public class ReportingArguments {
    private static final String EMPTY = "empty";

    @CommandLine.Option(names = {"--log"},
            description = "Set custom log level of a given package(s). You can provide a comma separated list of PACKAGE:LEVEL pairs", split = ",")
    private List<String> logData;

    @CommandLine.Option(names = {"--printExecutionStatistics"},
            description = "Print a summary of execution times for each endpoint and HTTP method. By default this will print a summary for each endpoint: max, min and average. If you want detailed reports you must supply --printExecutionStatistics=detailed")
    private boolean printExecutionStatistics;

    @CommandLine.Option(names = {"--printDetailedExecutionStatistics"},
            description = "Print detailed execution statistics with execution times for each request")
    private boolean printDetailedExecutionStatistics;

    @CommandLine.Option(names = {"--timestampReports"},
            description = "Output the report inside the 'cats-report' folder in a sub-folder with the current timestamp")
    private boolean timestampReports;

    @CommandLine.Option(names = {"--reportFormat"},
            description = "The format of the CATS report. Default: ${DEFAULT-VALUE}. You can use 'HTML_ONLY' if you want the report to not contain any Javascript. This is useful for large number of tests, as the page will render faster and also in CI environments due to Javascript content security policies.")
    private ReportFormat reportFormat = ReportFormat.HTML_JS;

    public List<String> getLogData() {
        return Optional.ofNullable(logData).orElse(Collections.emptyList());
    }

    public enum ReportFormat {
        HTML_ONLY, HTML_JS
    }
}
