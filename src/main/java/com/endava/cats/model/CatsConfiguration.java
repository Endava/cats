package com.endava.cats.model;

import com.endava.cats.http.HttpMethod;

import java.util.List;

/**
 * This class is used to store the configuration context for the Cats application.
 */
public record CatsConfiguration(String version, String contract, String basePath,
                                List<HttpMethod> httpMethods, int fuzzers, long totalFuzzers, int pathsToRun,
                                int totalPaths) {
}
