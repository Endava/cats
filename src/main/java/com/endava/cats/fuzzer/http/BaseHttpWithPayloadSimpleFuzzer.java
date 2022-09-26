package com.endava.cats.fuzzer.http;

import com.endava.cats.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

/**
 * This is a base class for Fuzzers that want to send invalid payloads for HTTP methods accepting bodies.
 * It expects a 4XX within the response.
 */
public abstract class BaseHttpWithPayloadSimpleFuzzer implements Fuzzer {
    protected final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;

    BaseHttpWithPayloadSimpleFuzzer(SimpleExecutor ce) {
        this.simpleExecutor = ce;
    }

    @Override
    public void fuzz(FuzzingData data) {
        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .expectedResponseCode(ResponseCodeFamily.FOURXX)
                        .fuzzingData(data)
                        .logger(logger)
                        .replaceRefData(false)
                        .runFilter(HttpMethod::requiresBody)
                        .scenario(this.getScenario())
                        .fuzzer(this)
                        .payload(this.getPayload(data))
                        .build()
        );
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    /**
     * Returns the scenario to be displayed in the test report.
     *
     * @return the test scenario
     */
    protected abstract String getScenario();

    /**
     * Returns the payload to be sent to the service.
     *
     * @param data the current FuzzingData
     * @return the payload to be sent to the service
     */
    protected abstract String getPayload(FuzzingData data);
}
