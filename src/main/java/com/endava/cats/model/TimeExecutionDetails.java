package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * Entity holding details about time execution details for test cases.
 */
@Builder
@Getter
@ToString
public class TimeExecutionDetails {
    private final String path;
    private final List<TimeExecution> executions;
    private final TimeExecution bestCase;
    private final TimeExecution worstCase;
    private final double average;
}