package com.endava.cats.args;

import lombok.Getter;
import picocli.CommandLine;

import javax.inject.Singleton;

/**
 * Holds all arguments related to API details.
 */
@Getter
@Singleton
public class ApiArguments {
    @CommandLine.Option(names = {"--maxRequestsPerMinute"},
            description = "Maximum number of requests per minute; this is useful when APIs have rate limiting implemented. Default: ${DEFAULT-VALUE}",
            defaultValue = "10000")
    private int maxRequestsPerMinute = 10000;

    @CommandLine.Option(names = {"--connectionTimeout"},
            description = "Time period in seconds which CATS should establish a connection with the server. Default: ${DEFAULT-VALUE}",
            defaultValue = "10")
    private int connectionTimeout = 10;

    @CommandLine.Option(names = {"--writeTimeout"},
            description = "Maximum time of inactivity in seconds between two data packets when sending the request to the server. Default: ${DEFAULT-VALUE}",
            defaultValue = "10")
    private int writeTimeout = 10;

    @CommandLine.Option(names = {"--readTimeout"},
            description = "Maximum time of inactivity in seconds between two data packets when waiting for the server's response. Default: ${DEFAULT-VALUE}",
            defaultValue = "10")
    private int readTimeout = 10;

    @CommandLine.Option(names = {"-c", "--contract"},
            description = "The OpenAPI contract",
            required = true)
    private String contract;

    @CommandLine.Option(names = {"-s", "--server"},
            description = "Base URL of the service",
            required = true)
    private String server;

    public boolean isRemoteContract() {
        return contract != null && contract.startsWith("http");
    }
}
