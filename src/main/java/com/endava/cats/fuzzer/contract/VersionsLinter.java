package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.OpenApiUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

/**
 * Checks if OpenAPI specifies any versioning information.
 */
@Linter
@Singleton
public class VersionsLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new VersionsLinter instance.
     *
     * @param tcl the test case listener
     */
    public VersionsLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the contract contains versioning information");
        testCaseListener.addExpectedResult(log, "Paths should contain versioning information either through paths, servers definition or versioning headers");

        boolean hasVersioning = !OpenApiUtils.getApiVersions(data.getOpenApi()).isEmpty();

        super.addDefaultsForPathAgnosticFuzzers();

        if (hasVersioning) {
            testCaseListener.reportResultInfo(log, data, "OpenAPI contract contains versioning information");
        } else {
            testCaseListener.reportResultError(log, data, "Versioning information not found", "OpenAPI contract does not contain versioning information");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "1";
    }

    @Override
    public String description() {
        return "verifies that a given path doesn't contain versioning information";
    }
}
