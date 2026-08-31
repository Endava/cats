package com.endava.cats.exception;

/**
 * Signals a user-requested stop at a safe boundary in the fuzzing lifecycle.
 */
public final class CatsExecutionCancelledException extends RuntimeException {
    private CatsExecutionCancelledException() {
        super("Execution cancelled by user", null, false, false);
    }

    /**
     * Stops execution when the current worker has been interrupted.
     */
    public static void check() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CatsExecutionCancelledException();
        }
    }
}
