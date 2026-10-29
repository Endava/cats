package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.fuzzer.contract.util.LinterVocabulary;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Checks that paths follow plural naming conventions.
 */
@Linter
@Singleton
public class PathPluralsLinter extends BaseLinter {
    private static final Set<String> NAMESPACE_PREFIXES = Set.of(
            "api", "rest", "bff", "internal", "external", "public", "private", "webhook", "graphql"
    );
    private static final Set<String> UNCOUNTABLE_EXTRAS = Set.of(
            "auth", "oauth", "oauth2", "sso", "login", "logout", "data"
    );

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

        List<String> pathElementsWithoutPlural = new ArrayList<>();
        for (int i = 0; i < pathWithoutVariables.size(); i++) {
            String pathElement = pathWithoutVariables.get(i);
            if (!isPluralOrExempt(pathElement, i)) {
                pathElementsWithoutPlural.add(pathElement);
            }
        }

        if (pathElementsWithoutPlural.size() > pathWithoutVariables.size() / 2) {
            return String.join(", ", pathElementsWithoutPlural);
        }

        return N_A;
    }

    private static boolean isPluralOrExempt(String pathElement, int index) {
        if (WordUtils.isPlural(pathElement)) {
            return true;
        }
        String lower = pathElement.toLowerCase(Locale.ROOT);
        if (index == 0 && NAMESPACE_PREFIXES.contains(lower)) {
            return true;
        }
        return LinterVocabulary.SINGLETON_SEGMENTS.contains(lower) || UNCOUNTABLE_EXTRAS.contains(lower);
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