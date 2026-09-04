package com.endava.cats.exception;

/**
 * Signals that a configured execution limit was reached at a safe test-case boundary.
 */
public final class CatsExecutionLimitReachedException extends RuntimeException {
    /**
     * Creates an exception describing the reached limit.
     *
     * @param message a user-facing description of the reached limit
     */
    public CatsExecutionLimitReachedException(String message) {
        super(message, null, false, false);
    }
}
