package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.headers.base.BaseRandomHeadersFuzzer;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsRandom;
import jakarta.inject.Singleton;

import java.util.function.Function;

/**
 * Sends a large number of random headers with alphanumeric names and values.
 */
@Singleton
@HeaderFuzzer
public class LargeNumberOfRandomAlphanumericHeadersFuzzer extends BaseRandomHeadersFuzzer {

    /**
     * Creates a new instance.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param testCaseListener    listener used to report test case progress
     * @param processingArguments used to hold configuration for how many headers to send
     */
    public LargeNumberOfRandomAlphanumericHeadersFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener, ProcessingArguments processingArguments) {
        super(simpleExecutor, testCaseListener, processingArguments);
    }

    @Override
    public String description() {
        return "send a 'happy' flow request with 10 000 extra random alphanumeric headers";
    }

    @Override
    protected Function<Integer, String> randomHeadersValueFunction() {
        return CatsRandom::alphanumeric;
    }
}
