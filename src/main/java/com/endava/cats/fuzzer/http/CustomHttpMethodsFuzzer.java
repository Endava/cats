package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Fuzzer that sends hypothetical http methods that should not be part of a standard REST API.
 */
@Singleton
@HttpFuzzer
public class CustomHttpMethodsFuzzer implements Fuzzer {
    private final List<String> fuzzedPaths = new ArrayList<>();
    private final HttpMethodFuzzerUtil httpMethodFuzzerUtil;


    /**
     * Creates a new NonRestHttpMethodsFuzzer instance.
     *
     * @param hmfu the utility class used to execute the logic
     */
    public CustomHttpMethodsFuzzer(HttpMethodFuzzerUtil hmfu) {
        this.httpMethodFuzzerUtil = hmfu;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!fuzzedPaths.contains(data.getPath())) {
            for (HttpMethod httpMethod : HttpMethod.hypotheticalMethods()) {
                httpMethodFuzzerUtil.process(this, data, httpMethod);
            }
            fuzzedPaths.add(data.getPath());
        }
    }

    @Override
    public String description() {
        return "iterate through a list of hypothetical HTTP methods that are not expected to be implemented by REST APIs";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}