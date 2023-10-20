package com.endava.cats.args;

import jakarta.inject.Singleton;
import lombok.Getter;
import picocli.CommandLine;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Holds all args related to Authentication details.
 */
@Singleton
@Getter
public class AuthArguments {
    @CommandLine.Option(names = {"--sslKeystore"},
            description = "Location of the keystore holding certificates used when authenticating calls using one-way or two-way SSL")
    private String sslKeystore;

    @CommandLine.Option(names = {"--sslKeystorePwd"},
            description = "The password of the sslKeystore")
    private String sslKeystorePwd;

    @CommandLine.Option(names = {"--sslKeyPwd"},
            description = "The password of the private key from the sslKeystore")
    private String sslKeyPwd;

    @CommandLine.Option(names = {"--basicAuth", "--basicauth"},
            description = "A username:password pair, in case the service uses basic auth")
    private String basicAuth;

    @CommandLine.Option(names = {"--proxyHost"},
            description = "The proxy server's host name")
    private String proxyHost;

    @CommandLine.Option(names = {"--proxyPort"},
            description = "The proxy server's port number")
    private int proxyPort;

    @CommandLine.Option(names = {"--authRefreshScript", "--ars"},
            description = "Script to get executed after --authRefreshInterval in order to get new auth credentials. " +
                    "The script will replace any headers that have @|bold,underline aut_script|@ as value. " +
                    "If you don't supply a --authRefreshInterval, but you supply a script, the script " +
                    "will be used to get the initial auth credentials.")
    private String authRefreshScript = "";

    @CommandLine.Option(names = {"--authRefreshInterval", "--ari"},
            description = "Amount of time in seconds after which to get new auth credentials")
    private int authRefreshInterval;


    /**
     * Checks if proxy details were supplied via the {@code --proxyXXX} arguments.
     *
     * @return true if proxy arguments are supplied, false otherwise
     */
    public boolean isProxySupplied() {
        return proxyHost != null && proxyPort != 0;
    }

    /**
     * Checks is basic auth details were supplied via the {@code --basicAuth} argument.
     *
     * @return true if basic auth details were supplied, false otherwise
     */
    public boolean isBasicAuthSupplied() {
        return basicAuth != null;
    }

    /**
     * Checks if SSL keystore was supplied via the {@code --sslKeystore} argument.
     *
     * @return true if a SSL keystore was supplied, false otherwise
     */
    public boolean isMutualTls() {
        return sslKeystore != null;
    }

    /**
     * Returns the Proxy if set or NO_PROXY otherwise.
     *
     * @return the Proxy settings supplied through args
     */
    public Proxy getProxy() {
        Proxy proxy = Proxy.NO_PROXY;
        if (isProxySupplied()) {
            proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
        }
        return proxy;
    }

    /**
     * Creates a basic auth header based on the supplied --basicAuth argument.
     *
     * @return base64 encoded basic auth header
     */
    public String getBasicAuthHeader() {
        byte[] encodedAuth = Base64.getEncoder().encode(this.basicAuth.getBytes(StandardCharsets.UTF_8));
        return "Basic " + new String(encodedAuth, StandardCharsets.UTF_8);
    }

    /**
     * Creates a Map with the following elements "auth_script"=--authRefreshScript argument
     * and "auth_refresh"=--authRefreshInterval.
     *
     * @return a Map with auth refresh details
     */
    public Map<String, String> getAuthScriptAsMap() {
        return Map.of("auth_script", this.getAuthRefreshScript(), "auth_refresh", String.valueOf(getAuthRefreshInterval()));
    }
}
