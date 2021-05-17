package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.fuzzer.headers.ExpectOnly4XXBaseHeadersFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;

import java.util.List;
import java.util.stream.Collectors;

public abstract class TrailingInvisibleCharsFuzzer extends ExpectOnly4XXBaseHeadersFuzzer {

    public TrailingInvisibleCharsFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public List<FuzzingStrategy> fuzzStrategy() {
        return this.getInvisibleChars()
                .stream().map(value -> FuzzingStrategy.trail().withData(value))
                .collect(Collectors.toList());
    }

    @Override
    public String description() {
        return "iterate through each header and " + typeOfDataSentToTheService();
    }

    abstract List<String> getInvisibleChars();
}