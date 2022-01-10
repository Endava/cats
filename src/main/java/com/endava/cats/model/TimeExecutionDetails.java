package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Builder
@Getter
@ToString
public class TimeExecutionDetails {
    private final String path;
    private final List<String> executions;
    private final String bestCase;
    private final String worstCase;
    private final double average;
}