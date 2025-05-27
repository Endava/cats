package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

/**
 * Checks if the current path has at least one operation.
 */
@Linter
@Singleton
public class EmptyPathsLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new EmptyPathsLinter instance.
     *
     * @param tcl the test case listener
     */
    public EmptyPathsLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the current path has at least one operation");
        testCaseListener.addExpectedResult(log, "Path has at least one operation");

        boolean isPathEmpty = data.getPathItem() == null || data.getPathItem().readOperations().isEmpty();

        if (isPathEmpty) {
            testCaseListener.reportResultError(log, data, "Path is empty", "The current path must define at least one operation");
        } else {
            testCaseListener.reportResultInfo(log, data, "Path is valid");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath();
    }

    @Override
    public String description() {
        return "verifies that the current path contains at least one operation";
    }
}