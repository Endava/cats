package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.OpenApiUtils;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

@LinterFuzzer
@Singleton
public class PathNounsLinterFuzzer extends BaseLinterFuzzer {
    private static final Pattern COMMON_VERBS_PATTERN = Pattern.compile("^(retrieve|get|create|update|delete|search|list|find|add|modify|upload|download|register|purchase|send|validate|process|approve|reject|cancel|execute|submit)[a-z][a-z]+$");

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    public PathNounsLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the path {} uses nouns to describe resources {}", data.getPath(), data.getMethod());
        testCaseListener.addExpectedResult(log, "Path elements must use nouns to describe resources");

        String checks = this.checkNouns(OpenApiUtils.getPathElements(data.getPath()));

        if (this.hasErrors(checks)) {
            testCaseListener.reportResultError(log, data, "Path elements not nouns",
                    "The following path elements are not nouns: {}", StringUtils.stripEnd(checks.trim(), ","));
        } else {
            testCaseListener.reportResultInfo(log, data, "Path elements use nouns to describe resources.");
        }
    }

    private String checkNouns(String[] pathElements) {
        return this.check(pathElements, pathElement -> OpenApiUtils.isNotAPathVariable(pathElement) && COMMON_VERBS_PATTERN.matcher(pathElement.toLowerCase(Locale.ROOT)).matches());
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that path elements use nouns to describe resources";
    }
}
