package com.endava.cats.aop;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@ToString
public class DryRunEntry {
    private String path;
    private String httpMethod;
    private String tests;
}
