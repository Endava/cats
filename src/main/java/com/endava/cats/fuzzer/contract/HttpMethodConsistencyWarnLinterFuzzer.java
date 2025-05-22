package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.fuzzer.contract.util.HttpMethodConsistencyAnalyzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@LinterFuzzer
@Singleton
public class HttpMethodConsistencyWarnLinterFuzzer extends BaseLinterFuzzer {
    private static final PrettyLogger LOG = PrettyLoggerFactory.getLogger(HttpMethodConsistencyWarnLinterFuzzer.class);

    private final HttpMethodConsistencyAnalyzer analyzer;

    @Inject
    public HttpMethodConsistencyWarnLinterFuzzer(TestCaseListener testCaseListener, HttpMethodConsistencyAnalyzer analyzer) {
        super(testCaseListener);
        this.analyzer = analyzer;
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(LOG, "Check optional REST methods (DELETE, PUT/PATCH) on item paths");
        testCaseListener.addExpectedResult(LOG, "Item paths should ideally support DELETE and at least one update method");
        super.addDefaultsForPathAgnosticFuzzers();

        HttpMethodConsistencyAnalyzer.ResourcePathData resourceData = analyzer.collectResourceData(data.getOpenApi());
        Map<String, List<String>> warnings = analyzeNonCriticalConsistency(resourceData);

        if (warnings.isEmpty()) {
            testCaseListener.reportResultInfo(LOG, data, "All item paths have expected optional REST methods");
        } else {
            StringBuilder warnBuilder = new StringBuilder();
            warnings.forEach((group, findings) ->
                    warnBuilder.append("- Group [").append(group).append("] ")
                            .append(String.join("; ", findings)).append("\n"));
            testCaseListener.reportResultWarn(LOG, data, "Non-critical HTTP method consistency issues",
                    "The following non-critical issues were found:%n%s".formatted(warnBuilder));
        }
    }

    private Map<String, List<String>> analyzeNonCriticalConsistency(HttpMethodConsistencyAnalyzer.ResourcePathData resourceData) {
        Map<String, List<String>> warnings = new HashMap<>();

        for (String group : resourceData.getGroups()) {
            Set<String> itemHttpMethods = resourceData.getItemMethods(group);
            List<String> groupFindings = new ArrayList<>();

            if (!itemHttpMethods.contains("delete")) {
                groupFindings.add("missing DELETE on item path [%s]".formatted(analyzer.deriveItemPathFromGroup(group)));
            }

            boolean hasUpdateMethod = HttpMethodConsistencyAnalyzer.UPDATE_METHODS.stream()
                    .anyMatch(itemHttpMethods::contains);
            if (!hasUpdateMethod) {
                groupFindings.add("missing at least one of PUT or PATCH on item path [%s]".formatted(analyzer.deriveItemPathFromGroup(group)));
            }

            if (!groupFindings.isEmpty()) {
                warnings.put(group, groupFindings);
            }
        }

        return warnings;
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "http-method-warnings";
    }

    @Override
    public String description() {
        return "flags missing optional REST methods (DELETE, PUT/PATCH) on item paths";
    }
}
