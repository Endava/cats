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

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("reportingLevel").value(reportingLevel).help("LEVEL this can be either INFO, WARN or ERROR. It can be used to suppress INFO logging and focus only on the reporting WARNS and/or ERRORS").build());
        args.add(CatsArg.builder().name("log").value(logData).help("PACKAGE:LEVEL set custom log level of a given package").build());
        args.add(CatsArg.builder().name("printExecutionStatistics").value(String.valueOf(this.printExecutionStatistics())).help("If supplied (no value needed), prints a summary of execution times for each endpoint and HTTP method").build());
    }

    public boolean printExecutionStatistics() {
        return !EMPTY.equalsIgnoreCase(printExecutionStatistics);
    }

    public boolean hasLogData() {
        return !EMPTY.equalsIgnoreCase(logData);
    }

}
