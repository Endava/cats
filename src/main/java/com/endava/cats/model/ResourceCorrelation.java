package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Describes a value obtained from a previously successful request and reused in a later request.
 */
@Builder
@Getter
public class ResourceCorrelation {
    private final String sourceMethod;
    private final String sourcePath;
    private final String sourceLocation;
    private final String targetLocation;
    private final String targetField;
    private final Object value;
}
