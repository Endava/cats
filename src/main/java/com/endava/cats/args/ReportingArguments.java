package com.endava.cats.args;

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

    @Value("${reportingLevel:info}")
    private String reportingLevel;
    @Value("${log:empty}")
    private String logData;
    @Value("${printExecutionStatistics:empty}")
    private String printExecutionStatistics;

    @Value("${arg.reporting.reportingLevel.help:help}")
    private String reportingLevelHelp;
    @Value("${arg.reporting.log.help:help}")
    private String logDataHelp;
    @Value("${arg.reporting.printExecutionStatistics.help:help}")
    private String printExecutionStatisticsHelp;


    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("reportingLevel").value(reportingLevel).help(reportingLevelHelp).build());
        args.add(CatsArg.builder().name("log").value(logData).help(logDataHelp).build());
        args.add(CatsArg.builder().name("printExecutionStatistics").value(String.valueOf(this.printExecutionStatistics())).help(printExecutionStatisticsHelp).build());
    }

    public boolean printExecutionStatistics() {
        return !EMPTY.equalsIgnoreCase(printExecutionStatistics);
    }

    public boolean hasLogData() {
        return !EMPTY.equalsIgnoreCase(logData);
    }

}
