package com.endava.cats.exception;

/**
 * The report folder has no problems computed by this CATS version, so it cannot be compared or triaged; re-run CATS.
 */
public class UnsupportedReportException extends RuntimeException {
    public UnsupportedReportException(String message) {
        super(message);
    }
}
