package com.endava.cats.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Describes a value obtained from a previously successful request and reused in a later request.
 */
@Builder
@Getter
public class ResourceCorrelation {
    private final String sourceMethod;
    private final String sourcePath;
    private final String sourceLocation;
    @NonNull
    private final RequestTarget target;
    private final Object value;

    public String getTargetLocation() {
        return target.getLocation().name().toLowerCase(java.util.Locale.ROOT);
    }

    public String getTargetField() {
        return target.getName();
    }
}
