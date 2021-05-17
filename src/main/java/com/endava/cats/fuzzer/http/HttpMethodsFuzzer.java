package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.HttpFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
@HttpFuzzer
@ConditionalOnProperty(value = "fuzzer.http.HttpMethodsFuzzer.enabled", havingValue = "true")
public class HttpMethodsFuzzer implements Fuzzer {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(HttpMethodsFuzzer.class);
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
            executeForOperation(data, PathItem::getPost, serviceCaller::call, HttpMethod.POST);
            executeForOperation(data, PathItem::getPut, serviceCaller::call, HttpMethod.PUT);
            executeForOperation(data, PathItem::getGet, serviceCaller::call, HttpMethod.GET);
            executeForOperation(data, PathItem::getPatch, serviceCaller::call, HttpMethod.PATCH);
            executeForOperation(data, PathItem::getDelete, serviceCaller::call, HttpMethod.DELETE);
            executeForOperation(data, PathItem::getTrace, serviceCaller::call, HttpMethod.TRACE);

            if (data.getPathItem().getGet() == null) {
                executeForOperation(data, PathItem::getHead, serviceCaller::call, HttpMethod.HEAD);
            }

        } else {
            LOGGER.skip("Skip path {} as already fuzzed!", data.getPath());
        }
    }

    private void executeForOperation(FuzzingData data, Function<PathItem, Operation> operation, Function<ServiceData, CatsResponse> serviceCall, HttpMethod httpMethod) {
        if (operation.apply(data.getPathItem()) == null) {
            testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data, serviceCall, httpMethod));
        }
    }

    private void process(FuzzingData data, Function<ServiceData, CatsResponse> f, HttpMethod httpMethod) {
        testCaseListener.addScenario(LOGGER, "Send a happy flow request with undocumented HTTP methods");
        testCaseListener.addExpectedResult(LOGGER, "Should get a 405 response code");
        CatsResponse response = f.apply(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders()).payload("").httpMethod(httpMethod).build());
        this.checkResponse(response);
        fuzzedPaths.add(data.getPath());
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
