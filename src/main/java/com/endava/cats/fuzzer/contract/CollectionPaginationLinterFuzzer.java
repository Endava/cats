package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@LinterFuzzer
@Singleton
public class CollectionPaginationLinterFuzzer extends BaseLinterFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    private final Set<String> paginationParams = Set.of(
            "limit", "offset", "page", "perpage", "per_page", "size",
            "cursor", "startafter", "next", "token"
    );

    public CollectionPaginationLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if GET operations on collection endpoints support pagination parameters");
        testCaseListener.addExpectedResult(log, "Collection GETs should define pagination query parameters like limit, offset, page, size, or cursor");

        OpenAPI openAPI = data.getOpenApi();
        List<String> violations = new ArrayList<>();

        openAPI.getPaths().forEach((path, pathItem) -> {
            Operation getOp = pathItem.getGet();
            if (getOp == null) return;
            if (isItemPath(path)) return;

            Set<String> queryParams = collectQueryParams(pathItem, getOp);

            boolean hasPagination = queryParams.stream()
                    .map(String::toLowerCase)
                    .anyMatch(paginationParams::contains);

            if (!hasPagination) {
                violations.add("- GET on [%s] is missing pagination query parameters".formatted(path));
            }
        });

        super.addDefaultsForPathAgnosticFuzzers();

        if (!violations.isEmpty()) {
            testCaseListener.reportResultWarn(
                    log,
                    data,
                    "Missing pagination support on GET collection endpoints",
                    String.join("\n", violations)
            );
        } else {
            testCaseListener.reportResultInfo(log, data, "All collection GET endpoints declare pagination support");
        }
    }

    private Set<String> collectQueryParams(PathItem pathItem, Operation getOp) {
        List<Parameter> allParams = new ArrayList<>();

        if (pathItem.getParameters() != null) {
            allParams.addAll(pathItem.getParameters());
        }
        if (getOp.getParameters() != null) {
            allParams.addAll(getOp.getParameters());
        }

        return allParams.stream()
                .filter(p -> "query".equalsIgnoreCase(p.getIn()))
                .map(Parameter::getName)
                .collect(Collectors.toSet());
    }

    private boolean isItemPath(String path) {
        return path.matches(".*/\\{[^/]+}$");
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "collection-pagination-linter";
    }

    @Override
    public String description() {
        return "verifies that all GET operations on collection endpoints support pagination via query parameters like limit, offset, page, or cursor";
    }
}