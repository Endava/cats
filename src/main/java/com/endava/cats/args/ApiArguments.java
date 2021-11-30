package com.endava.cats.args;

import com.endava.cats.util.CatsUtil;
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
    private final List<CatsArg> args = new ArrayList<>();
    private final String contractHelp = "LOCATION_OF_THE_CONTRACT";
    private final String serverHelp = "BASE_URL_OF_THE_SERVICE";
    private final String maxRequestsPerMinuteHelp = "maximum number of requests per minute; this is useful when APIs have rate limiting implemented; default is 100";
    private final String connectionTimeoutHelp = "time period in seconds which CATS should establish a connection with the server; default is 10 seconds";
    private final String writeTimeoutHelp = "maximum time of inactivity in seconds between two data packets when sending the request to the server; default is 10 seconds";
    private final String readTimeoutHelp = "maximum time of inactivity in seconds between two data packets when waiting for the server's response; default is 10 seconds";

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
        return !CatsUtil.isArgumentValid(contract);
    }

    /**
     * Checks if there was any server address supplied.
     *
     * @return true if a server address was supplied, false otherwise
     */
    public boolean isServerEmpty() {
        return !CatsUtil.isArgumentValid(server);
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
