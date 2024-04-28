package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;

/**
 * Fuzzer that inserts random values in request bodies.
 */
@HttpFuzzer
@Singleton
public class InsertRandomValuesInBodyFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new InsertRandomValuesInBodyFuzzer instance.
     *
     * @param ce the executor
     */
    @Inject
    InsertRandomValuesInBodyFuzzer(SimpleExecutor ce) {
        this.simpleExecutor = ce;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!JsonUtils.isEmptyPayload(data.getPayload())) {
            for (String maliciousPayload : UnicodeGenerator.getInvalidJsons()) {
                char firstChar = data.getPayload().charAt(0);
                String finalPayload = firstChar + maliciousPayload + "," + data.getPayload().substring(1);

                simpleExecutor.execute(
                        SimpleExecutorContext.builder()
                                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                                .fuzzingData(data)
                                .logger(logger)
                                .replaceRefData(false)
                                .scenario("Insert invalid data %s within a valid json request body".formatted(maliciousPayload))
                                .fuzzer(this)
                                .payload(finalPayload)
                                .build());
            }
        }
    }

    @Override
    public String description() {
        return "insert invalid data within a valid request body";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return Arrays.asList(HttpMethod.GET, HttpMethod.DELETE);
    }
}
