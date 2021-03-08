package com.endava.cats.args;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
public class CheckArguments {
    public static final String EMPTY = "empty";
    @Getter
    private final List<CatsArg> args = new ArrayList<>();

    @Value("${checkHeaders:empty}")
    private String checkHeaders;
    @Value("${checkFields:empty}")
    private String checkFields;
    @Value("${checkHttp:empty}")
    private String checkHttp;
    @Value("${checkContract:empty}")
    private String checkContract;

    @Value("${arg.check.checkHeaders.help:help}")
    private String checkHeadersHelp;
    @Value("${arg.check.checkFields.help:help}")
    private String checkFieldsHelp;
    @Value("${arg.check.checkHttp.help:help}")
    private String checkHttpHelp;
    @Value("${arg.check.checkContract.help:help}")
    private String checkContractHelp;

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("checkHeaders").value(String.valueOf(this.checkHeaders())).help(checkHeadersHelp).build());
        args.add(CatsArg.builder().name("checkFields").value(String.valueOf(this.checkFields())).help(checkFieldsHelp).build());
        args.add(CatsArg.builder().name("checkHttp").value(String.valueOf(this.checkHttp())).help(checkHttpHelp).build());
        args.add(CatsArg.builder().name("checkContract").value(String.valueOf(this.checkContract())).help(checkContractHelp).build());
    }

    public boolean checkHeaders() {
        return !EMPTY.equalsIgnoreCase(this.checkHeaders);
    }

    public boolean checkFields() {
        return !EMPTY.equalsIgnoreCase(this.checkFields);
    }

    public boolean checkHttp() {
        return !EMPTY.equalsIgnoreCase(this.checkHttp);
    }

    public boolean checkContract() {
        return !EMPTY.equalsIgnoreCase(this.checkContract);
    }
}
