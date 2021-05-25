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
    private final HttpMethodFuzzerUtil httpMethodFuzzerUtil;

    @Autowired
    public HttpMethodsFuzzer(ServiceCaller sc, TestCaseListener lr, HttpMethodFuzzerUtil hmfu) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.httpMethodFuzzerUtil = hmfu;
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
            fuzzedPaths.add(data.getPath());
        } else {
            LOGGER.skip("Skip path {} as already fuzzed!", data.getPath());
        }
    }

    private void executeForOperation(FuzzingData data, Function<PathItem, Operation> operation, Function<ServiceData, CatsResponse> serviceCall, HttpMethod httpMethod) {
        if (operation.apply(data.getPathItem()) == null) {
            testCaseListener.createAndExecuteTest(LOGGER, this, () -> httpMethodFuzzerUtil.process(data, serviceCall, httpMethod));
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
