package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.headers.base.ExpectOnly4XXBaseHeadersFuzzer;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;


/**
 *
 */
@Singleton
@HeaderFuzzer
public class VeryLargeStringsInHeadersFuzzer extends ExpectOnly4XXBaseHeadersFuzzer {
    private final ProcessingArguments processingArguments;

    public VeryLargeStringsInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr, ProcessingArguments pa) {
        super(sc, lr);
        this.processingArguments = pa;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "large values";
    }

    @Override
    protected List<FuzzingStrategy> fuzzStrategy() {
        return Collections.singletonList(
                FuzzingStrategy.replace().withData(
                        StringGenerator.generateLargeString(processingArguments.getLargeStringsSize() / 4)));
    }

    @Override
    public String description() {
        return "iterate through each header and send requests with large values in the targeted header";
    }

    @Override
    public boolean matchResponseSchema() {
        return false;
    }
}
