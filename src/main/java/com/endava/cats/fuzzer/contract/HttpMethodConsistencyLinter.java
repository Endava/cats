package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.fuzzer.contract.util.HttpMethodConsistencyAnalyzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks REST HTTP method consistency across resource groups (POST on collection paths
 * and GET, PUT/PATCH, DELETE on item paths) and reports missing methods as warnings.
 */
@Linter
@Singleton
public class HttpMethodConsistencyLinter extends BaseLinter {
    private static final PrettyLogger LOG = PrettyLoggerFactory.getLogger(HttpMethodConsistencyLinter.class);

    private final HttpMethodConsistencyAnalyzer analyzer;

    /**
     * Creates a new HttpMethodConsistencyLinter instance.
     *
     * @param testCaseListener the test case listener
     * @param analyzer         the HTTP method consistency analyzer
     */
    @Inject
    public HttpMethodConsistencyLinter(TestCaseListener testCaseListener, HttpMethodConsistencyAnalyzer analyzer) {
        super(testCaseListener);
        this.analyzer = analyzer;
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(LOG, "Check REST HTTP method consistency (POST on collection paths, GET, PUT/PATCH, and DELETE on item paths)");
        testCaseListener.addExpectedResult(LOG, "Resource groups should ideally support POST on collection paths and GET, PUT/PATCH, and DELETE on item paths");
        super.addDefaultsForPathAgnosticFuzzers();

        HttpMethodConsistencyAnalyzer.ResourcePathData resourceData = analyzer.collectResourceData(data.getOpenApi());
        Map<String, List<String>> warnings = analyzeConsistency(resourceData);

        if (warnings.isEmpty()) {
            testCaseListener.reportResultInfo(LOG, data, "All resource groups have consistent REST HTTP methods");
        } else {
            StringBuilder warnBuilder = new StringBuilder();
            warnings.forEach((group, findings) ->
                    warnBuilder.append("- Group [").append(group).append("] ")
                            .append(String.join("; ", findings)).append("\n"));
            testCaseListener.reportResultWarn(LOG, data, "HTTP method consistency issues",
                    "The following HTTP method consistency issues were found:%n%s".formatted(warnBuilder));
        }
    }

    private Map<String, List<String>> analyzeConsistency(HttpMethodConsistencyAnalyzer.ResourcePathData resourceData) {
        Map<String, List<String>> warnings = new LinkedHashMap<>();

        for (String group : resourceData.getGroups()) {
            Set<String> paths = resourceData.getPathsForGroup(group);
            boolean hasItemPath = paths.stream().anyMatch(analyzer::isItemPath);
            if (!hasItemPath) {
                continue;
            }

            boolean hasCollectionPath = paths.stream().anyMatch(p -> !analyzer.isItemPath(p));
            Set<String> itemHttpMethods = resourceData.getItemMethods(group);
            boolean itemHasGetOrUpdate = itemHttpMethods.contains("get")
                    || itemHttpMethods.contains("put")
                    || itemHttpMethods.contains("patch");
            if (!hasCollectionPath && !itemHasGetOrUpdate) {
                continue;
            }

            List<String> groupFindings = new ArrayList<>();

            if (!hasCollectionPath || paths.stream()
                    .filter(p -> !analyzer.isItemPath(p))
                    .noneMatch(p -> resourceData.pathMethods().getOrDefault(p, Set.of()).contains("post"))) {
                groupFindings.add("missing POST on collection path [%s]".formatted(analyzer.deriveCollectionPathFromGroup(group)));
            }

            if (!itemHttpMethods.contains("get")) {
                groupFindings.add("missing GET on item path [%s]".formatted(analyzer.deriveItemPathFromGroup(group)));
            }

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
        return "http-method-consistency";
    }

    @Override
    public String description() {
        return "flags missing REST HTTP methods (POST on collection paths and GET, PUT/PATCH, DELETE on item paths) across resource groups";
    }
}
