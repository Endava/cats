package com.endava.cats.model;

import java.util.Objects;

/** Describes the single authoritative outcome of an execution session. */
public record RunOutcome(Status status, String details) {
    private static final String COMPLETED_DETAILS = "All selected fuzzers completed";

    public RunOutcome {
        Objects.requireNonNull(status, "status");
        details = Objects.requireNonNullElse(details, "");
    }

    public enum Status {
        COMPLETED,
        LIMIT_REACHED,
        CANCELLED,
        FAILED
    }

    public static RunOutcome completed() {
        return new RunOutcome(Status.COMPLETED, COMPLETED_DETAILS);
    }

    public static RunOutcome limitReached(String details) {
        return new RunOutcome(Status.LIMIT_REACHED, details);
    }

    public static RunOutcome cancelled(String details) {
        return new RunOutcome(Status.CANCELLED, details);
    }

    public static RunOutcome failed(String details) {
        return new RunOutcome(Status.FAILED, details);
    }
}
