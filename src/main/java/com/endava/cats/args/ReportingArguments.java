package com.endava.cats.args;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class ReportingArguments {
    @Value("${reportingLevel:info}")
    private String reportingLevel;
    @Value("${log:empty}")
    private String logData;
    @Value("${printExecutionStatistics:empty}")
    private String printExecutionStatistics;

}
