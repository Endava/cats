package com.endava.cats.args;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CheckArguments {
    public static final String EMPTY = "empty";

    @Value("${checkHeaders:empty}")
    private String checkHeaders;
    @Value("${checkFields:empty}")
    private String checkFields;
    @Value("${checkHttp:empty}")
    private String checkHttp;
    @Value("${checkContract:empty}")
    private String checkContract;

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
