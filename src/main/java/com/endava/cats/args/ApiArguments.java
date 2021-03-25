package com.endava.cats.args;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
@Getter
public class ApiArguments {
    public static final String EMPTY = "empty";
    private final List<CatsArg> args = new ArrayList<>();

    @Value("${contract:empty}")
    private String contract;
    @Value("${server:empty}")
    private String server;

    @Value("${arg.api.contract.help:help}")
    private String contractHelp;
    @Value("${arg.api.server.help:help}")
    private String serverHelp;

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("contract").value(contract).help(contractHelp).build());
        args.add(CatsArg.builder().name("server").value(server).help(serverHelp).build());
    }

    public boolean isContractEmpty() {
        return EMPTY.equalsIgnoreCase(contract);
    }

    public boolean isServerEmpty() {
        return EMPTY.equalsIgnoreCase(server);
    }

    public boolean isRemoteContract() {
        return this.contract.startsWith("http");
    }

}
