package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.Operation;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Component
public class HttpMethodsFuzzer implements Fuzzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpMethodsFuzzer.class);
    private final List<String> fuzzedPaths = new ArrayList<>();
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    @Autowired
    public HttpMethodsFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    public void fuzz(FuzzingData data) {
        if (!fuzzedPaths.contains(data.getPath())) {

            Operation post = data.getPathItem().getPost();

            if (post == null) {
                testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data.getPath(), data.getHeaders(), serviceCaller::post));
            }

            Operation get = data.getPathItem().getGet();
            if (get == null) {
                testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data.getPath(), data.getHeaders(), serviceCaller::get));
            }

            Operation put = data.getPathItem().getPut();
            if (put == null) {
                testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data.getPath(), data.getHeaders(), serviceCaller::put));
            }

            Operation delete = data.getPathItem().getDelete();
            if (delete == null) {
                testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data.getPath(), data.getHeaders(), serviceCaller::delete));
            }

            Operation patch = data.getPathItem().getPatch();
            if (patch == null) {
                testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data.getPath(), data.getHeaders(), serviceCaller::patch));
            }

            Operation head = data.getPathItem().getHead();
            if (head == null && data.getPathItem().getGet() == null) {
                testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data.getPath(), data.getHeaders(), serviceCaller::head));
            }

            Operation trace = data.getPathItem().getTrace();
            if (trace == null) {
                testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data.getPath(), data.getHeaders(), serviceCaller::trace));
            }
        } else {
            LOGGER.info("Skip path {} as already fuzzed!", data.getPath());
        }
    }

    private void process(String path, Set<CatsHeader> headers, Function<ServiceData, CatsResponse> f) {
        testCaseListener.addScenario(LOGGER, "Scenario: send a happy flow request with undocumented HTTP methods");
        testCaseListener.addExpectedResult(LOGGER, "Expected result: should get a 405 response code");
        try {
            CatsResponse response = f.apply(ServiceData.builder().relativePath(path).headers(headers).payload("").build());
            this.checkResponse(response);
            fuzzedPaths.add(path);
        } catch (Exception e) {
            testCaseListener.reportError(LOGGER, "Fuzzer [{}] failed due to [{}]", this.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void checkResponse(CatsResponse response) {
        if (response.getResponseCode() == HttpStatus.SC_METHOD_NOT_ALLOWED) {
            testCaseListener.reportInfo(LOGGER, "Request failed as expected for http method [{}] with response code [{}]",
                    response.getHttpMethod(), response.getResponseCode());
        } else if (ResponseCodeFamily.is2xxCode(response.getResponseCode())) {
            testCaseListener.reportError(LOGGER, "Request succeeded unexpectedly for http method [{}]: expected [{}], actual [{}]",
                    response.getHttpMethod(), HttpStatus.SC_METHOD_NOT_ALLOWED, response.getResponseCode());
        } else {
            testCaseListener.reportWarn(LOGGER, "Unexpected response code for http method [{}]: expected [{}], actual [{}]",
                    response.getHttpMethod(), HttpStatus.SC_METHOD_NOT_ALLOWED, response.getResponseCode());
        }
    }

    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "iterate through each undocumented HTTP method and send an empty request";
    }
}
