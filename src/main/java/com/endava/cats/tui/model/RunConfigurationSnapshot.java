package com.endava.cats.tui.model;

import com.endava.cats.model.CatsConfiguration;

import java.util.List;

/**
 * Immutable view of the effective CATS run configuration.
 */
public record RunConfigurationSnapshot(String version, String contract, String basePath,
                                       List<String> httpMethods, int configuredFuzzers, long totalFuzzers,
                                       int configuredPaths, int totalPaths,
                                       int maxRequestsPerMinute, int maxRateLimitRetries, int maxRetryAfterInSec, boolean blackbox) {
    public RunConfigurationSnapshot(String version, String contract, String basePath,
                                    List<String> httpMethods, int configuredFuzzers, long totalFuzzers,
                                    int configuredPaths, int totalPaths,
                                    int maxRequestsPerMinute, int maxRateLimitRetries, int maxRetryAfterInSec) {
        this(version, contract, basePath, httpMethods, configuredFuzzers, totalFuzzers, configuredPaths, totalPaths,
                maxRequestsPerMinute, maxRateLimitRetries, maxRetryAfterInSec, false);
    }

    public RunConfigurationSnapshot(String version, String contract, String basePath,
                                    List<String> httpMethods, int configuredFuzzers, long totalFuzzers,
                                    int configuredPaths, int totalPaths) {
        this(version, contract, basePath, httpMethods, configuredFuzzers, totalFuzzers, configuredPaths, totalPaths,
                10000, 3, 30);
    }

    /**
     * Creates a snapshot from the CATS configuration.
     *
     * @param configuration source configuration
     * @return immutable configuration snapshot
     */
    public static RunConfigurationSnapshot from(CatsConfiguration configuration) {
        return from(configuration, false);
    }

    public static RunConfigurationSnapshot from(CatsConfiguration configuration, boolean blackbox) {
        return new RunConfigurationSnapshot(configuration.version(), configuration.contract(), configuration.basePath(),
                configuration.httpMethods().stream().map(Enum::name).toList(), configuration.fuzzers(),
                configuration.totalFuzzers(), configuration.pathsToRun(), configuration.totalPaths(),
                configuration.maxRequestsPerMinute(), configuration.maxRateLimitRetries(),
                configuration.maxRetryAfterInSec(), blackbox);
    }
}
