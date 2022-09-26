package com.endava.cats.fuzzer.http;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Fuzzer that sends a "happy" flow request with no fuzzing applied.
 */
@Singleton
@HttpFuzzer
public class HappyFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(HappyFuzzer.class);
    private final SimpleExecutor simpleExecutor;

    @Inject
    public HappyFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    public void fuzz(FuzzingData data) {
        simpleExecutor.execute(SimpleExecutorContext.builder()
                .fuzzingData(data)
                .expectedResponseCode(ResponseCodeFamily.TWOXX)
                .fuzzer(this)
                .payload(data.getPayload())
                .scenario("Send a 'happy' flow request with all fields and all headers")
                .logger(logger)
                .build());
    }

    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "send a request with all fields and headers populated";
    }
}
