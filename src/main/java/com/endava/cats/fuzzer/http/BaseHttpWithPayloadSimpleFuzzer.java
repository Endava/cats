package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This is a base class for Fuzzers that want to send invalid payloads for HTTP methods accepting bodies.
 * It expects a 4XX within the response.
 */
public abstract class BaseHttpWithPayloadSimpleFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private final List<String> fuzzedPaths = new ArrayList<>();

    BaseHttpWithPayloadSimpleFuzzer(SimpleExecutor ce) {
        this.simpleExecutor = ce;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.debug("Skipping fuzzer as payload is empty");
            return;
        }
        String runKey = data.getPath() + data.getMethod();
        if (fuzzedPaths.contains(runKey)) {
            logger.skip("Skipping fuzzer as it already fuzzer. Most likely a oneOf/anyOf payload");
            return;
        }
        fuzzedPaths.add(runKey);

        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .expectedResponseCode(this.getExpectedResponseCode(data))
                        .fuzzingData(data)
                        .logger(logger)
                        .replaceRefData(false)
                        .scenario(this.getScenario())
                        .fuzzer(this)
                        .validJson(false)
                        .payload(this.getPayload(data))
                        .build()
        );
    }

    /**
     * By default, expect a 4XX response. This can be overridden by subclasses to expect other response codes.
     *
     * @param data the data to fuzz
     * @return the expected response code family for the fuzzing operation
     */
    protected ResponseCodeFamily getExpectedResponseCode(FuzzingData data) {
        return ResponseCodeFamilyPredefined.FOURXX;
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

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }
}
