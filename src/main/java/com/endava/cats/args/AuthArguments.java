package com.endava.cats.args;

import com.endava.cats.model.CatsRequest;
import lombok.Getter;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Holds all args related to Authentication details.
 */
@Component
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
            description = "Supplies a username:password pair, in case the service uses basic auth")
    private String basicAuth;

    @CommandLine.Option(names = {"--proxyHost"},
            description = "The proxy server's host name")
    private String proxyHost;

    @CommandLine.Option(names = {"--proxyPort"},
            description = "The proxy server's port number")
    private int proxyPort;


    public boolean isProxySupplied() {
        return proxyHost != null && proxyPort != 0;
    }

    public boolean isBasicAuthSupplied() {
        return basicAuth != null;
    }

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

    public CatsRequest.Header getBasicAuthHeader() {
        byte[] encodedAuth = Base64.getEncoder().encode(this.basicAuth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth, StandardCharsets.UTF_8);
        return new CatsRequest.Header("Authorization", authHeader);
    }
}
