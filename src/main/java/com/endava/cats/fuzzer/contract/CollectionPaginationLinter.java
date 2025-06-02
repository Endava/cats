package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Linter
@Singleton
public class CollectionPaginationLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    private final Set<String> paginationParams = Set.of(
            "limit", "offset", "page", "perpage", "per_page", "size",
            "cursor", "startafter", "next", "token"
    );

    public CollectionPaginationLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        if (data.getMethod() != HttpMethod.GET || isItemPath(data.getPath())) {
            testCaseListener.skipTest(log, "Skipping pagination check for non-GET operations");
            return;
        }

        testCaseListener.addScenario(log, "Check if GET operations on collection endpoints support pagination parameters");
        testCaseListener.addExpectedResult(log, "Collection GETs should define pagination query parameters like limit, offset, page, size, or cursor");

        List<String> violations = new ArrayList<>();

        Operation getOp = data.getPathItem().getGet();

        Set<String> queryParams = collectQueryParams(data.getPathItem(), getOp);

        boolean hasPagination = queryParams.stream()
                .map(String::toLowerCase)
                .anyMatch(paginationParams::contains);

        if (!hasPagination) {
            violations.add("Operation is missing pagination query parameters");
        }

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
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that all GET operations on collection endpoints support pagination via query parameters like limit, offset, page, or cursor";
    }
}