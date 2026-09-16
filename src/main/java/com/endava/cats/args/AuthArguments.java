package com.endava.cats.args;

import com.endava.cats.exception.CatsException;
import jakarta.inject.Singleton;
import lombok.Getter;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Holds all args related to Authentication details.
 */
@Singleton
@Getter
public class AuthArguments {
    @CommandLine.Option(names = {"--sslKeystore"},
            description = "Location of the JKS keystore holding the client certificate and private key for mutual TLS")
    private String sslKeystore;

    @CommandLine.Option(names = {"--sslKeystorePwd"},
            description = "The password of the sslKeystore")
    private String sslKeystorePwd;

    @CommandLine.Option(names = {"--sslKeyPwd"},
            description = "The password of the private key from the sslKeystore")
    private String sslKeyPwd;

    @CommandLine.Option(names = {"--insecure"},
            description = "Disable TLS certificate and hostname verification. Use only for trusted test environments")
    private boolean insecure;

    @CommandLine.Option(names = {"--envFile"},
            description = "Load fallback environment variables from a dotenv file instead of the auto-detected ./.env")
    private File envFile;

    @CommandLine.Option(names = {"--noEnvFile", "--no-env-file"},
            description = "Disable automatic loading of ./.env")
    private boolean noEnvFile;

    private Path loadedEnvFile;
    private Map<String, String> envFileVariables = Map.of();

    @CommandLine.Option(names = {"--basicAuth", "--basicauth"},
            description = "A username:password pair, when using basic auth")
    private String basicAuth;

    @CommandLine.Option(names = {"--proxyHost"},
            description = "The proxy server's host name")
    private String proxyHost;

    @CommandLine.Option(names = {"--proxyPort"},
            description = "The proxy server's port number")
    private int proxyPort;

    @CommandLine.Option(names = {"--authRefreshScript", "--ars"},
            description = "Script to get executed after --authRefreshInterval in order to get new auth credentials. " +
                    "The script will replace any headers that have @|bold,underline auth_script|@ as value. " +
                    "If a --authRefreshInterval is not supplied, but a script is, the script " +
                    "will be used to get the initial auth credentials.")
    private String authRefreshScript = "";

    @CommandLine.Option(names = {"--authRefreshInterval", "--ari"},
            description = "Amount of time in seconds after which to get new auth credentials")
    private int authRefreshInterval;

    @CommandLine.Option(names = {"--wfcAuth"},
            description = "A Web Fuzzing Commons authentication YAML/JSON file. CATS will resolve the selected auth entry and apply its headers, cookies or query params to requests.")
    private File wfcAuthFile;

    @CommandLine.Option(names = {"--wfcAuthName"},
            description = "The name of the authentication entry to use from the Web Fuzzing Commons auth file. If omitted, CATS uses the first entry.")
    private String wfcAuthName;


    /**
     * Checks if proxy details were supplied via the {@code --proxyXXX} arguments.
     *
     * @return true if proxy arguments are supplied, false otherwise
     */
    public boolean isProxySupplied() {
        return proxyHost != null && proxyPort != 0;
    }

    /**
     * Checks if basic auth details were supplied via the {@code --basicAuth} argument.
     *
     * @return true if basic auth details were supplied, false otherwise
     */
    public boolean isBasicAuthSupplied() {
        return basicAuth != null;
    }

    /**
     * Checks if a Web Fuzzing Commons auth file was supplied.
     *
     * @return true if WFC auth should be loaded, false otherwise
     */
    public boolean isWfcAuthSupplied() {
        return wfcAuthFile != null;
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

    public Map<String, String> getDynamicVariablesContext() {
        Map<String, String> context = new LinkedHashMap<>(getEnvironmentVariables());
        context.putAll(getAuthScriptAsMap());
        return Map.copyOf(context);
    }

    public Map<String, String> getEnvironmentVariables() {
        Path currentFile = selectedEnvironmentFile().orElse(null);
        if (currentFile == null) {
            loadedEnvFile = null;
            envFileVariables = Map.of();
            return envFileVariables;
        }
        if (currentFile.equals(loadedEnvFile)) {
            return envFileVariables;
        }
        try {
            Map<String, String> variables = new LinkedHashMap<>();
            for (String sourceLine : Files.readAllLines(currentFile, StandardCharsets.UTF_8)) {
                String line = sourceLine.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).strip();
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    throw new CatsException("Invalid dotenv entry in " + currentFile + ": " + sourceLine);
                }
                String name = line.substring(0, separator).strip();
                String value = stripQuotes(line.substring(separator + 1).strip());
                variables.put(name, value);
            }
            loadedEnvFile = currentFile;
            envFileVariables = Map.copyOf(variables);
            return envFileVariables;
        } catch (IOException e) {
            throw new CatsException("Unable to read --envFile " + currentFile, e);
        }
    }

    public String getEnvironmentFileStatus() {
        Optional<Path> selected = selectedEnvironmentFile();
        if (noEnvFile) {
            return "disabled";
        }
        if (selected.isEmpty()) {
            return "none";
        }
        return selected.get() + (envFile == null ? " (auto-detected)" : "");
    }

    Optional<Path> selectedEnvironmentFile() {
        if (noEnvFile && envFile != null) {
            throw new CatsException("--envFile and --noEnvFile cannot be used together");
        }
        if (noEnvFile) {
            return Optional.empty();
        }
        if (envFile != null) {
            return Optional.of(envFile.toPath().toAbsolutePath().normalize());
        }
        Path defaultFile = defaultEnvironmentFile();
        return Files.isRegularFile(defaultFile) ? Optional.of(defaultFile) : Optional.empty();
    }

    Path defaultEnvironmentFile() {
        return Path.of(".env").toAbsolutePath().normalize();
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
