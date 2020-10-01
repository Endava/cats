package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@HeaderFuzzer
public class SpacesOnlyInHeadersFuzzer extends Expect4XXBaseHeadersFuzzer {

    @Autowired
    public SpacesOnlyInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "spaces only";
    }

    @Override
    protected FuzzingStrategy fuzzStrategy() {
        return FuzzingStrategy.replace().withData("   ");
    }

    @Override
    public String description() {
        return "iterate through each header and send requests with empty spaces in the targeted header";
    }
}
