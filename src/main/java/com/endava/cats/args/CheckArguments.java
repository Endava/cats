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

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("checkHeaders").value(checkHeaders).help("If supplied (no value needed), it will only run the Header Fuzzers").build());
        args.add(CatsArg.builder().name("checkFields").value(checkFields).help("If supplied (no value needed), it will only run the Field Fuzzers").build());
        args.add(CatsArg.builder().name("checkHttp").value(checkHttp).help("If supplied (no value needed), it will only run the HTTP Fuzzers").build());
        args.add(CatsArg.builder().name("checkContract").value(checkContract).help("If supplied (no value needed), it will only run the ContractInfo Fuzzers").build());
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
