package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.OpenApiUtils;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.stream.IntStream;

@LinterFuzzer
@Singleton
public class PathPluralsLinterFuzzer extends BaseLinterFuzzer {
    private static final String PLURAL_END = "s";

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    public PathPluralsLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the path {} uses pluralization to describe resources {}", data.getPath(), data.getMethod());
        testCaseListener.addExpectedResult(log, "Path elements must use plurals to describe resources.");

        String checks = this.checkPlurals(OpenApiUtils.getPathElements(data.getPath()));

        if (this.hasErrors(checks)) {
            testCaseListener.reportResultError(log, data, "Path elements not plural",
                    "The following path elements are not using pluralization: {}", StringUtils.stripEnd(checks.trim(), ","));
        } else {
            testCaseListener.reportResultInfo(log, data, "Path elements use pluralization to describe resources.");
        }
    }

    private String checkPlurals(String[] pathElements) {
        String[] filteredPathElements = IntStream.range(0, pathElements.length)
                .filter(i -> i % 2 == 0)
                .mapToObj(i -> pathElements[i])
                .toArray(String[]::new);

        return this.check(filteredPathElements, pathElement -> OpenApiUtils.isNotAPathVariable(pathElement) && !pathElement.endsWith(PLURAL_END));
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