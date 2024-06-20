package com.endava.cats.report;

/**
 * Represents an error that occurred during the progress of the fuzzer.
 */
public record ProgressError(String fuzzer, String path, String httpMethod, String message) {
}
