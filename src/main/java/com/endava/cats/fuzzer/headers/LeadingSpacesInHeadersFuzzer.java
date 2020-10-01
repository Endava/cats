package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@HeaderFuzzer
public class LeadingSpacesInHeadersFuzzer extends Expect2XXBaseHeadersFuzzer {

    @Autowired
    public LeadingSpacesInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "prefix spaces";
    }

    @Override
    protected FuzzingStrategy fuzzStrategy() {
        return FuzzingStrategy.prefix().withData("    ");
    }

    @Override
    public String description() {
        return "iterate through each header and send requests with spaces prefixing the value in the targeted header";
    }
}
