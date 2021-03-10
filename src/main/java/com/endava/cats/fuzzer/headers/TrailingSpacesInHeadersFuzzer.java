package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@HeaderFuzzer
@ConditionalOnProperty(value = "fuzzer.headers.TrailingSpacesInHeadersFuzzer.enabled", havingValue = "true")
public class TrailingSpacesInHeadersFuzzer extends Expect2XXBaseHeadersFuzzer {

    @Autowired
    public TrailingSpacesInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "trailing spaces";
    }

    @Override
    protected FuzzingStrategy fuzzStrategy() {
        return FuzzingStrategy.trail().withData("    ");
    }

    @Override
    public String description() {
        return "iterate through each header and send requests with trailing spaces in the targeted header";
    }
}
