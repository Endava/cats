package com.endava.cats.fuzzer.http;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
@HttpFuzzer
public class NonRestHttpMethodsFuzzer implements Fuzzer {
    private final List<String> fuzzedPaths = new ArrayList<>();
    private final HttpMethodFuzzerUtil httpMethodFuzzerUtil;

    public NonRestHttpMethodsFuzzer(HttpMethodFuzzerUtil hmfu) {
        this.httpMethodFuzzerUtil = hmfu;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!fuzzedPaths.contains(data.getPath())) {
            for (HttpMethod httpMethod : HttpMethod.nonRestMethods()) {
                httpMethodFuzzerUtil.process(this, data, httpMethod);
            }
            fuzzedPaths.add(data.getPath());
        }
    }

    @Override
    public String description() {
        return "iterate through a list of HTTP method specific to the WebDav protocol that are not expected to be implemented by REST APIs";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
