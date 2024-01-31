package com.endava.cats.aop;

/**
 * Record representing a dry run entry with information about the path, HTTP method, and associated tests.
 *
 * @param path       The path associated with the dry run entry.
 * @param httpMethod The HTTP method used in the dry run.
 * @param tests      A string containing information about the tests associated with the dry run entry.
 */
public record DryRunEntry(String path, String httpMethod, String tests) {
}
