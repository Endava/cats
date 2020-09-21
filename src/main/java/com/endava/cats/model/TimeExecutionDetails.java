package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Builder
@Getter
@ToString
public class TimeExecutionDetails {
    private String path;
    private List<String> executions;
    private String bestCase;
    private String worstCase;
    private double average;
}
