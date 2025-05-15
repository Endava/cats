package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@LinterFuzzer
@Singleton
public class DeleteHasBodyLinterFuzzer extends BaseLinterFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new instance of subclasses.
     *
     * @param tcl the test case listener
     */
    protected DeleteHasBodyLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        if (data.getPathItem().getDelete() == null) {
            testCaseListener.skipTest(log, "DELETE method not present");
            return;
        }
        String expectedResult = "DELETE methods should not have a body";
        testCaseListener.addScenario(log, "Check if DELETE methods have a body");
        testCaseListener.addExpectedResult(log, expectedResult);

        boolean hasRequestBodyInRoot = data.getPathItem().getDelete().getRequestBody() != null;
        boolean hasRequestBodyInParameters = Optional.ofNullable(data.getPathItem().getDelete().getParameters())
                .orElse(List.of())
                .stream().anyMatch(param -> param.getName().equals("body"));

        if (hasRequestBodyInRoot || hasRequestBodyInParameters) {
            testCaseListener.reportResultError(log, data, "DELETE has body",
                    "DELETE method should not have a body");
        } else {
            testCaseListener.reportResultInfo(log, data, "DELETE method does not have a body");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath();
    }

    @Override
    public String description() {
        return "checks if DELETE methods have a body";
    }
}
