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
 * Checks collection names using the shared path segment classification.
 */
@Linter
@Singleton
public class PathPluralsLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new PathPluralsLinter instance.
     *
     * @param tcl the test case listener
     */
    public PathPluralsLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the path {} uses pluralization to describe resources {}", data.getPath(), data.getMethod());
        testCaseListener.addExpectedResult(log, "Use plural collection nouns; identifiers, singleton resources, namespaces and operations are exempt.");
        PathNamingRules.Analysis analysis = PathNamingRules.analyze(data);
        if (!analysis.pluralErrors().isEmpty()) {
            testCaseListener.reportResultError(log, data, "Path elements not plural",
                    "Some of the following path elements are not using pluralization: {}", String.join("\n", analysis.pluralErrors()));
        } else if (analysis.nounErrors().isEmpty() && !analysis.warnings().isEmpty()) {
            testCaseListener.reportResultWarn(log, data, "Uncertain path naming roles",
                    "Review singular resource or operation names: {}", String.join("\n", analysis.warnings()));
        } else {
            testCaseListener.reportResultInfo(log, data, "Path elements use pluralization to describe resources.");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that collection segments use plural nouns and flags singular or verb-prefixed names whose role is uncertain";
    }
}
