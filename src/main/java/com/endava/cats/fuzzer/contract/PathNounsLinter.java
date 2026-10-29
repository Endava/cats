package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Checks that paths are using nouns rather than verbs.
 */
@Linter
@Singleton
public class PathNounsLinter extends BaseLinter {
    private static final Set<String> COMMON_VERBS = Set.of(
            "retrieve", "get", "create", "update", "delete", "search", "list", "find",
            "add", "modify", "upload", "download", "register", "purchase", "send",
            "validate", "process", "approve", "reject", "cancel", "execute", "submit"
    );

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
        return CatsUtil.check(pathElements, pathElement -> {
            if (!OpenApiUtils.isNotAPathVariable(pathElement)) {
                return false;
            }
            List<String> words = WordUtils.splitWords(pathElement);
            return words.size() >= 2 && COMMON_VERBS.contains(words.getFirst().toLowerCase(Locale.ROOT));
        });
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
