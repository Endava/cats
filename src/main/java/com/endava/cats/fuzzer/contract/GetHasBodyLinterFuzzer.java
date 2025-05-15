package com.endava.cats.fuzzer.contract;


import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
@LinterFuzzer
public class GetHasBodyLinterFuzzer extends BaseLinterFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new instance of subclasses.
     *
     * @param tcl the test case listener
     */
    protected GetHasBodyLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        if (data.getPathItem().getGet() == null) {
            testCaseListener.skipTest(log, "GET method not present");
            return;
        }
        String expectedResult = "GET methods should not have a body";
        testCaseListener.addScenario(log, "Check if GET methods have a body");
        testCaseListener.addExpectedResult(log, expectedResult);

        boolean hasRequestBodyInRoot = data.getPathItem().getGet().getRequestBody() != null;
        boolean hasRequestBodyInParameters = Optional.ofNullable(data.getPathItem().getGet().getParameters())
                .orElse(List.of())
                .stream().anyMatch(param -> param.getName().equals("body"));

        if (hasRequestBodyInRoot || hasRequestBodyInParameters) {
            testCaseListener.reportResultError(log, data, "GET has body",
                    "GET method should not have a body");
        } else {
            testCaseListener.reportResultInfo(log, data, "GET method does not have a body");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath();
    }

    @Override
    public String description() {
        return "checks if GET methods have a body";
    }
}
