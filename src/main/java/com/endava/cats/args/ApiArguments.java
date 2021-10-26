package com.endava.cats.args;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds all arguments related to API details.
 */
@Component
@Getter
public class ApiArguments {
    public static final String EMPTY = "empty";
    private final List<CatsArg> args = new ArrayList<>();

    @Value("${contract:empty}")
    private String contract;
    @Value("${server:empty}")
    private String server;
    @Value("${maxRequestsPerMinute:empty}")
    private String maxRequestsPerMinute;
    @Value("${connectionTimeout:10}")
    private int connectionTimeout;
    @Value("${writeTimeout:10}")
    private int writeTimeout;
    @Value("${readTimeout:10}")
    private int readTimeout;


    @Value("${arg.api.contract.help:help}")
    private String contractHelp;
    @Value("${arg.api.server.help:help}")
    private String serverHelp;
    @Value("${arg.api.maxRequestsPerMinute.help:help}")
    private String maxRequestsPerMinuteHelp;
    @Value("${arg.api.connectionTimeout.help:help}")
    private String connectionTimeoutHelp;
    @Value("${arg.api.writeTimeout.help:help}")
    private String writeTimeoutHelp;
    @Value("${arg.api.readTimeout.help:help}")
    private String readTimeoutHelp;

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("contract").value(contract).help(contractHelp).build());
        args.add(CatsArg.builder().name("server").value(server).help(serverHelp).build());
        args.add(CatsArg.builder().name("maxRequestsPerMinute").value(maxRequestsPerMinute).help(maxRequestsPerMinuteHelp).build());
        args.add(CatsArg.builder().name("connectionTimeout").value(String.valueOf(connectionTimeout)).help(connectionTimeoutHelp).build());
        args.add(CatsArg.builder().name("writeTimeout").value(String.valueOf(writeTimeout)).help(writeTimeoutHelp).build());
        args.add(CatsArg.builder().name("readTimeout").value(String.valueOf(readTimeout)).help(readTimeoutHelp).build());
    }

    /**
     * Checks if there was any contract supplied.
     *
     * @return true if a contract was supplied, false otherwise
     */
    public boolean isContractEmpty() {
        return EMPTY.equalsIgnoreCase(contract);
    }

    /**
     * Checks if there was any server address supplied.
     *
     * @return true if a server address was supplied, false otherwise
     */
    public boolean isServerEmpty() {
        return EMPTY.equalsIgnoreCase(server);
    }

    /**
     * Checks if there supplied contract is from an http address.
     *
     * @return true if a http contract, false otherwise
     */
    public boolean isRemoteContract() {
        return this.contract.startsWith("http");
    }

}
