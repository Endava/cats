package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks that http response codes are valid (between 100 and 599).
 */
@LinterFuzzer
@Singleton
public class HttpStatusCodeInRangeLinterFuzzer extends BaseLinterFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new HttpStatusCodeInRangeLinterFuzzer instance.
     *
     * @param tcl the test case listener
     */
    public HttpStatusCodeInRangeLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the response codes defined for the current path and HTTP method {} are valid HTTP codes i.e. between 100 and 599", data.getMethod());
        testCaseListener.addExpectedResult(log, "All defined response codes must be between 100 and 599");

        Set<String> notMatchingResponseCodes = data.getResponseCodes().stream()
                .filter(code -> !code.equalsIgnoreCase("default") && !ResponseCodeFamily.isValidCode(code)).collect(Collectors.toSet());

        if (notMatchingResponseCodes.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All defined response codes are valid!");
        } else {
            testCaseListener.reportResultError(log, data, "Invalid response codes", "The following response codes are not valid: {}", notMatchingResponseCodes);
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that all HTTP response codes are within the range of 100 to 599";
    }
}
