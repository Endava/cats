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

    @Value("${arg.reporting.timestampReports.help:help}")
    private String timestampReportsHelp;
    @Value("${arg.reporting.log.help:help}")
    private String logDataHelp;
    @Value("${arg.reporting.printExecutionStatistics.help:help}")
    private String printExecutionStatisticsHelp;
    @Value("${arg.reporting.reportFormat.help:help}")
    private String reportFormatHelp;


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
