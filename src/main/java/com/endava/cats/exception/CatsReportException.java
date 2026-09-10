package com.endava.cats.exception;

/** Signals that one or more requested report artifacts could not be generated. */
public final class CatsReportException extends CatsException {
    public CatsReportException(String message, Exception cause) {
        super(message, cause);
    }
}
