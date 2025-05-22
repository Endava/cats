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
public class HttpMethodConsistencyErrorLinterFuzzer extends BaseLinterFuzzer {
    private static final PrettyLogger LOG = PrettyLoggerFactory.getLogger(HttpMethodConsistencyErrorLinterFuzzer.class);

    private final HttpMethodConsistencyAnalyzer analyzer;

    @Inject
    public HttpMethodConsistencyErrorLinterFuzzer(TestCaseListener testCaseListener, HttpMethodConsistencyAnalyzer analyzer) {
        super(testCaseListener);
        this.analyzer = analyzer;
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(LOG, "Check required HTTP methods: GET on item paths and POST on collection paths");
        testCaseListener.addExpectedResult(LOG, "Each resource group must have GET on item paths and POST on collection paths where applicable");
        super.addDefaultsForPathAgnosticFuzzers();

        HttpMethodConsistencyAnalyzer.ResourcePathData resourceData = analyzer.collectResourceData(data.getOpenApi());
        Map<String, List<String>> errors = analyzeCriticalConsistency(resourceData);

        if (errors.isEmpty()) {
            testCaseListener.reportResultInfo(LOG, data, "All resource groups have required GET on item paths and POST on collection paths");
        } else {
            StringBuilder errorBuilder = new StringBuilder();
            errors.forEach((group, findings) ->
                    errorBuilder.append("- Group [").append(group).append("] ")
                            .append(String.join("; ", findings)).append("\n"));
            testCaseListener.reportResultError(LOG, data, "Critical HTTP method consistency issues",
                    "The following critical issues were found:%n%s".formatted(errorBuilder));
        }
    }

    private Map<String, List<String>> analyzeCriticalConsistency(HttpMethodConsistencyAnalyzer.ResourcePathData resourceData) {
        Map<String, List<String>> errors = new HashMap<>();

        for (String group : resourceData.getGroups()) {
            Set<String> paths = resourceData.getPathsForGroup(group);
            List<String> groupFindings = new ArrayList<>();

            boolean hasCollectionPath = paths.stream().anyMatch(p -> !analyzer.isItemPath(p));
            boolean hasItemPath = paths.stream().anyMatch(analyzer::isItemPath);

            if (!hasCollectionPath || paths.stream()
                    .filter(p -> !analyzer.isItemPath(p))
                    .noneMatch(p -> resourceData.pathMethods().getOrDefault(p, Set.of()).contains("post"))) {
                groupFindings.add("missing POST on collection path [%s]".formatted(analyzer.deriveCollectionPathFromGroup(group)));
            }

            if (!hasItemPath || paths.stream()
                    .filter(analyzer::isItemPath)
                    .noneMatch(p -> resourceData.pathMethods().getOrDefault(p, Set.of()).contains("get"))) {
                groupFindings.add("missing GET on item path [%s]".formatted(analyzer.deriveItemPathFromGroup(group)));
            }

            if (!groupFindings.isEmpty()) {
                errors.put(group, groupFindings);
            }
        }

        return errors;
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "http-method-critical";
    }

    @Override
    public String description() {
        return "flags missing critical HTTP methods: GET on item paths and POST on collection paths";
    }
}
