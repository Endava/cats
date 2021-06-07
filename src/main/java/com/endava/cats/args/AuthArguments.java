package com.endava.cats.args;

import com.endava.cats.model.CatsRequest;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Holds all args related to Authentication details.
 */
@Component
@Getter
public class AuthArguments {
    public static final String EMPTY = "empty";
    private final List<CatsArg> args = new ArrayList<>();
    @Value("${sslKeystore:empty}")
    private String sslKeystore;
    @Value("${sslKeystorePwd:empty}")
    private String sslKeystorePwd;
    @Value("${sslKeyPwd:empty}")
    private String sslKeyPwd;
    @Value("${basicauth:empty}")
    private String basicAuth;
    @Value("${proxyHost:empty}")
    private String proxyHost;
    @Value("${proxyPort:0}")
    private int proxyPort;

    @Value("${arg.auth.sslKeystore.help:help}")
    private String sslKeystoreHelp;
    @Value("${arg.auth.sslKeystorePwd.help:help}")
    private String sslKeystorePwdHelp;
    @Value("${arg.auth.sslKeyPwd.help:help}")
    private String sslKeyPwdHelp;
    @Value("${arg.auth.basicAuth.help:help}")
    private String basicAuthHelp;
    @Value("${arg.auth.proxyHost.help:help}")
    private String proxyHostHelp;
    @Value("${arg.auth.proxyPort.help:help}")
    private String proxyPortHelp;

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("proxyPort").value(String.valueOf(proxyPort)).help(proxyPortHelp).build());
        args.add(CatsArg.builder().name("proxyHost").value(proxyHost).help(proxyHostHelp).build());
        args.add(CatsArg.builder().name("sslKeystore").value(sslKeystore).help(sslKeystoreHelp).build());
        args.add(CatsArg.builder().name("sslKeystorePwd").value(sslKeystorePwd).help(sslKeystorePwdHelp).build());
        args.add(CatsArg.builder().name("sslKeyPwd").value(sslKeyPwd).help(sslKeyPwdHelp).build());
        args.add(CatsArg.builder().name("basicauth").value(basicAuth).help(basicAuthHelp).build());
    }

    public boolean isBasicAuthSupplied() {
        return !EMPTY.equalsIgnoreCase(basicAuth);
    }

    public boolean isMutualTls() {
        return !EMPTY.equalsIgnoreCase(sslKeystore);
    }

    public boolean isProxySupplied() {
        return !EMPTY.equalsIgnoreCase(proxyHost);
    }

    /**
     * Returns the Proxy if set or NO_PROXY otherwise
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
     * Returns the basic auth header if supplied. This method does not do any checks. It assumes the calling method already performed
     * the {@link #isBasicAuthSupplied()} check.
     *
     * @return the basic auth Header
     */
    public CatsRequest.Header getBasicAuthHeader() {
        byte[] encodedAuth = Base64.getEncoder().encode(this.basicAuth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth, StandardCharsets.UTF_8);
        return new CatsRequest.Header("Authorization", authHeader);
    }
}
