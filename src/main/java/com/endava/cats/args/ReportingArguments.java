package com.endava.cats.args;

import com.endava.cats.util.CatsUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
@Getter
public class ReportingArguments {
    private static final String EMPTY = "empty";
    private final List<CatsArg> args = new ArrayList<>();

    @Value("${log:empty}")
    private String logData;
    @Value("${printExecutionStatistics:empty}")
    private String printExecutionStatistics;
    @Value("${timestampReports:empty}")
    private String timestampReports;
    @Value("${reportFormat:htmlJs}")
    private String reportFormat;

    private final String timestampReportsHelp = "If supplied (no value needed), it will output the report still inside the 'cats-report' folder, but in a sub-folder with the current timestamp";
    private final String logDataHelp = "PACKAGE:LEVEL set custom log level of a given package. You can provide a comma separated list of PACKAGE:LEVEL pairs";
    private final String printExecutionStatisticsHelp = "If supplied (no value needed), prints a summary of execution times for each endpoint and HTTP method. By default this will print a summary for each endpoint: max, min and average. If you want detailed reports you must supply --printExecutionStatistics=detailed";
    private final String reportFormatHelp = "FORMAT specifies the format of the CATS report. You can use 'htmlOnly' if you want the report to not contain any Javascript. This is useful for large number of tests, as the page will render faster and also in CI environments due to Javascript content security policies. Default is 'htmlJs' which is the original CATS single page report format.";


    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("log").value(logData).help(logDataHelp).build());
        args.add(CatsArg.builder().name("printExecutionStatistics").value(String.valueOf(this.printExecutionStatistics())).help(printExecutionStatisticsHelp).build());
        args.add(CatsArg.builder().name("timestampReports").value(timestampReports).help(timestampReportsHelp).build());
        args.add(CatsArg.builder().name("reportFormat").value(reportFormat).help(reportFormatHelp).build());
    }

    public boolean printExecutionStatistics() {
        return !EMPTY.equalsIgnoreCase(printExecutionStatistics);
    }

    public boolean printDetailedExecutionStatistics() {
        return "detailed".equalsIgnoreCase(printExecutionStatistics);
    }

    public boolean hasLogData() {
        return CatsUtil.isArgumentValid(logData);
    }

    public boolean isTimestampReports() {
        return !EMPTY.equalsIgnoreCase(timestampReports);
    }

    public ReportFormat getReportFormat() {
        if (reportFormat.equalsIgnoreCase("htmlOnly")) {
            return ReportFormat.HTML_ONLY;
        }
        return ReportFormat.HTML_JS;
    }

    public enum ReportFormat {
        HTML_ONLY, HTML_JS
    }
}
