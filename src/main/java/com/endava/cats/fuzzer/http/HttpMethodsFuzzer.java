package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Singleton
@HttpFuzzer
public class HttpMethodsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(HttpMethodsFuzzer.class);
    private final List<String> fuzzedPaths = new ArrayList<>();
    private final HttpMethodFuzzerUtil httpMethodFuzzerUtil;

    @Inject
    public HttpMethodsFuzzer(HttpMethodFuzzerUtil hmfu) {
        this.httpMethodFuzzerUtil = hmfu;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!fuzzedPaths.contains(data.getPath())) {
            executeForOperation(data, PathItem::getPost, HttpMethod.POST);
            executeForOperation(data, PathItem::getPut, HttpMethod.PUT);
            executeForOperation(data, PathItem::getGet, HttpMethod.GET);
            executeForOperation(data, PathItem::getPatch, HttpMethod.PATCH);
            executeForOperation(data, PathItem::getDelete, HttpMethod.DELETE);
            executeForOperation(data, PathItem::getTrace, HttpMethod.TRACE);

            if (data.getPathItem().getGet() == null) {
                executeForOperation(data, PathItem::getHead, HttpMethod.HEAD);
            }
            fuzzedPaths.add(data.getPath());
        } else {
            logger.skip("Skip path {} as already fuzzed!", data.getPath());
        }
    }

    private void executeForOperation(FuzzingData data, Function<PathItem, Operation> operation, HttpMethod httpMethod) {
        if (operation.apply(data.getPathItem()) == null) {
            httpMethodFuzzerUtil.process(this, data, httpMethod);
        }
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "iterate through each undocumented HTTP method and send an empty request";
    }
}
