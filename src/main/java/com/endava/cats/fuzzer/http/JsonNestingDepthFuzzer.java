package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.RobustnessResponseReporter;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.PayloadFormat;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;

/** Probes JSON parser nesting limits with 24 documents, each smaller than 8 KiB. */
@Singleton
@HttpFuzzer
public class JsonNestingDepthFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor executor;
    private final RobustnessResponseReporter reporter;
    private final Set<Operation> visited = new HashSet<>();

    public JsonNestingDepthFuzzer(SimpleExecutor executor, RobustnessResponseReporter reporter) {
        this.executor = executor;
        this.reporter = reporter;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (isApplicableTo(data) && visited.add(new Operation(data.getMethod(), data.getPath()))) {
            JsonNestingInputs.probes().forEach(probe -> execute(data, probe));
        }
    }

    private void execute(FuzzingData data, JsonNestingInputs.Probe probe) {
        executor.execute(SimpleExecutorContext.builder().fuzzingData(data).fuzzer(this).logger(logger)
                .scenario(probe.description()).payload(probe.payload()).validJson(false).replaceRefData(false)
                .mutationTarget(RequestTarget.requestBody()).responseProcessor((response, context) -> reporter.report(logger, context, response)).expectedResponseCode(RobustnessResponseReporter.expectedResponses())
                .matchResponseResult(false).matchResponseContentType(false).build());
    }

    @Override
    public boolean isApplicableTo(FuzzingData data) {
        return data.getPayloadFormat() == PayloadFormat.JSON && data.getMethod() != null && HttpMethod.requiresBody(data.getMethod());
    }

    @Override
    public String description() {
        return "checks JSON parser robustness with complete and truncated nesting at depths 32, 128, 512 and 1024";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }

    private record Operation(HttpMethod method, String path) {
    }
}
