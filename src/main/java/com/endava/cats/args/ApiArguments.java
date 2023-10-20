package com.endava.cats.args;

import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine;

/**
 * Holds all arguments related to API details.
 */
@Getter
@Singleton
public class ApiArguments {
    @CommandLine.Option(names = {"--maxRequestsPerMinute"},
            description = "Maximum number of requests per minute; this is useful when APIs have rate limiting implemented. Default: @|bold,underline ${DEFAULT-VALUE}|@",
            defaultValue = "10000")
    private int maxRequestsPerMinute = 10000;

    @CommandLine.Option(names = {"--connectionTimeout"},
            description = "Time period in seconds which CATS should establish a connection with the server. Default: @|bold,underline ${DEFAULT-VALUE}|@",
            defaultValue = "10")
    private int connectionTimeout = 10;

    @CommandLine.Option(names = {"--writeTimeout"},
            description = "Maximum time of inactivity in seconds between two data packets when sending the request to the server. Default: @|bold,underline ${DEFAULT-VALUE}|@",
            defaultValue = "10")
    private int writeTimeout = 10;

    @CommandLine.Option(names = {"--readTimeout"},
            description = "Maximum time of inactivity in seconds between two data packets when waiting for the server's response. Default: @|bold,underline ${DEFAULT-VALUE}|@",
            defaultValue = "10")
    private int readTimeout = 10;

    @CommandLine.Option(names = {"--userAgent"},
            description = "The user agent to be set in the User-Agent HTTP header. Default: @|bold,underline cats/${app.version}|@")
    private String userAgent;

    @Setter
    @CommandLine.Option(names = {"-c", "--contract"},
            description = "The OpenAPI contract")
    private String contract;

    @Setter
    @CommandLine.Option(names = {"-s", "--server"},
            description = "Base URL of the service")
    private String server;

    /**
     * Verifies if the supplied OpenAPI spec is from a local location or a http one.
     *
     * @return true if the contract is from a http location, false otherwise
     */
    public boolean isRemoteContract() {
        return contract != null && contract.startsWith("http");
    }

    /**
     * Validates the required {@code --contract} and {@code --server} arguments are present.
     *
     * @param spec the PicoCli command spec
     */
    public void validateRequired(CommandLine.Model.CommandSpec spec) {
        if (this.contract == null) {
            throw new CommandLine.ParameterException(spec.commandLine(), "Missing required option --contract=<contract>");
        } else if (this.server == null) {
            throw new CommandLine.ParameterException(spec.commandLine(), "Missing required option --server=<server>");
        }
    }

    /**
     * Sets a custom user agent based on the CATS version.
     *
     * @param version the current CATS version
     */
    public void setUserAgent(String version) {
        if (userAgent == null) {
            userAgent = "cats/" + version;
        }
    }
}
