package com.endava.cats.fuzzer.http;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
@HttpFuzzer
public class NonRestHttpMethodsFuzzer implements Fuzzer {

    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(NonRestHttpMethodsFuzzer.class);
    private final List<String> fuzzedPaths = new ArrayList<>();
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final HttpMethodFuzzerUtil httpMethodFuzzerUtil;

    public NonRestHttpMethodsFuzzer(ServiceCaller sc, TestCaseListener lr, HttpMethodFuzzerUtil hmfu) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.httpMethodFuzzerUtil = hmfu;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!fuzzedPaths.contains(data.getPath())) {
            for (HttpMethod httpMethod : HttpMethod.nonRestMethods()) {
                testCaseListener.createAndExecuteTest(LOGGER, this, () -> httpMethodFuzzerUtil.process(data, serviceCaller::call, httpMethod));
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
        return this.getClass().getSimpleName().replace("_Subclass", "");
    }
}
