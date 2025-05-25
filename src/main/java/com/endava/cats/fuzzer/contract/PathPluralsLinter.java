package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Checks that paths follow plural naming conventions.
 */
@Linter
@Singleton
public class PathPluralsLinter extends BaseLinter {
    private static final String PLURAL_END = "s";

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
        testCaseListener.addExpectedResult(log, "Path elements must use plurals to describe resources.");

        String checks = this.checkPlurals(OpenApiUtils.getPathElements(data.getPath()));

        if (this.hasErrors(checks)) {
            testCaseListener.reportResultError(log, data, "Path elements not plural",
                    "Some of the following path elements are not using pluralization: {}", StringUtils.stripEnd(checks.trim(), ","));
        } else {
            testCaseListener.reportResultInfo(log, data, "Path elements use pluralization to describe resources.");
        }
    }

    private String checkPlurals(String[] pathElements) {
        List<String> pathWithoutVariables = Arrays.stream(pathElements)
                .filter(OpenApiUtils::isNotAPathVariable)
                .toList();

        List<String> pathElementsWithoutPlural = pathWithoutVariables.stream()
                .filter(pathElement -> !pathElement.endsWith(PLURAL_END))
                .toList();

        if (pathElementsWithoutPlural.size() > pathWithoutVariables.size() / 2) {
            return String.join(", ", pathElementsWithoutPlural);
        }

        return N_A;
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that path elements uses pluralization to describe resources";
    }
}