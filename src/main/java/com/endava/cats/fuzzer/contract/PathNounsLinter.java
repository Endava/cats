package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.fuzzer.contract.util.PathNamingRules;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;


/**
 * Checks resource noun phrases and the placement and method of custom operations.
 */
@Linter
@Singleton
public class PathNounsLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new PathNounsLinter instance.
     *
     * @param tcl the test case listener
     */
    public PathNounsLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check that path {} uses resource nouns and well-formed custom operations for {}", data.getPath(), data.getMethod());
        testCaseListener.addExpectedResult(log, "Use resource noun phrases and terminal custom operations; avoid redundant CRUD names");
        PathNamingRules.Analysis analysis = PathNamingRules.analyze(data);
        if (!analysis.nounErrors().isEmpty()) {
            testCaseListener.reportResultError(log, data, "Path resource or operation naming violations",
                    "The following path segments violate resource or operation naming rules: {}", String.join("\n", analysis.nounErrors()));
        } else {
            testCaseListener.reportResultInfo(log, data, "Path segments use resource nouns and well-formed custom operations.");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that path segments use resource nouns and that custom operations are verb-only, terminal, nested under a resource, and invoked with a matching HTTP method";
    }
}
