package com.endava.cats.args;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds all args related to category of Fuzzers to run.
 */
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
    @Value("${includeWhitespaces:empty}")
    private String includeWhitespaces;
    @Value("${includeEmojis:empty}")
    private String includeEmojis;
    @Value("${includeControlChars:empty}")
    private String includeControlChars;


    @Value("${arg.check.checkHeaders.help:help}")
    private String checkHeadersHelp;
    @Value("${arg.check.checkFields.help:help}")
    private String checkFieldsHelp;
    @Value("${arg.check.checkHttp.help:help}")
    private String checkHttpHelp;
    @Value("${arg.check.checkContract.help:help}")
    private String checkContractHelp;
    @Value("${arg.check.includeWhitespaces.help:help}")
    private String includeWhitespacesHelp;
    @Value("${arg.check.includeEmojis.help:help}")
    private String includeEmojisHelp;
    @Value("${arg.check.includeControlChars.help:help}")
    private String includeControlCharsHelp;

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("checkHeaders").value(String.valueOf(this.checkHeaders())).help(checkHeadersHelp).build());
        args.add(CatsArg.builder().name("checkFields").value(String.valueOf(this.checkFields())).help(checkFieldsHelp).build());
        args.add(CatsArg.builder().name("checkHttp").value(String.valueOf(this.checkHttp())).help(checkHttpHelp).build());
        args.add(CatsArg.builder().name("checkContract").value(String.valueOf(this.checkContract())).help(checkContractHelp).build());
        args.add(CatsArg.builder().name("includeControlChars").value(String.valueOf(this.includeControlChars())).help(includeControlCharsHelp).build());
        args.add(CatsArg.builder().name("includeEmojis").value(String.valueOf(this.includeEmojis())).help(includeEmojisHelp).build());
        args.add(CatsArg.builder().name("includeControlChars").value(String.valueOf(this.includeWhitespaces())).help(includeWhitespacesHelp).build());
    }

    public boolean includeControlChars() {
        return !EMPTY.equalsIgnoreCase(this.includeControlChars);
    }

    public boolean includeEmojis() {
        return !EMPTY.equalsIgnoreCase(this.includeEmojis);
    }

    public boolean includeWhitespaces() {
        return !EMPTY.equalsIgnoreCase(this.includeWhitespaces);
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
