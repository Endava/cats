package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.OpenApiUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifies bidirectional consistency between URL path template variables ({param})
 * and declared OpenAPI path parameters (in: path, required: true).
 */
@Linter
@Singleton
public class PathParameterDeclarationLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new PathParameterDeclarationLinter instance.
     *
     * @param tcl the test case listener
     */
    public PathParameterDeclarationLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if URL path variables match declared 'in: path' parameters for {} {}", data.getMethod(), data.getPath());
        testCaseListener.addExpectedResult(log, "Every {param} in the URL path must be declared with 'in: path' and 'required: true', and no undeclared/orphan path parameters should exist");

        Operation operation = data.getPathItem() != null && data.getMethod() != null
                ? HttpMethod.getOperation(data.getMethod(), data.getPathItem())
                : null;
        if (operation == null) {
            testCaseListener.skipTest(log, "Operation not present");
            return;
        }

        Set<String> urlVariables = data.getPath() != null
                ? OpenApiUtils.getPathVariables(data.getPath())
                : Set.of();
        Map<String, Parameter> declaredPathParams = collectPathParameters(data, data.getPathItem(), operation);

        List<String> violations = new ArrayList<>();

        for (String urlVar : urlVariables) {
            if (!declaredPathParams.containsKey(urlVar)) {
                violations.add("Path variable '{%s}' in URL is not declared in parameters with 'in: path'".formatted(urlVar));
            }
        }

        for (Map.Entry<String, Parameter> entry : declaredPathParams.entrySet()) {
            String paramName = entry.getKey();
            Parameter param = entry.getValue();
            if (!urlVariables.contains(paramName)) {
                violations.add("Declared path parameter '%s' is not present as '{%s}' in URL path '%s'"
                        .formatted(paramName, paramName, data.getPath()));
            }
            if (!Boolean.TRUE.equals(param.getRequired())) {
                violations.add("Path parameter '%s' must specify 'required: true'".formatted(paramName));
            }
        }

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All path parameters are properly declared and aligned with the URL template");
        } else {
            testCaseListener.reportResultError(log, data, "Invalid path parameter declarations", String.join("\n", violations));
        }
    }

    private static Map<String, Parameter> collectPathParameters(FuzzingData data, PathItem pathItem, Operation operation) {
        Map<String, Parameter> pathParams = new LinkedHashMap<>();
        for (Parameter resolved : OpenApiUtils.collectPathAndOperationParameters(pathItem, operation, data.getOpenApi())) {
            if ("path".equalsIgnoreCase(resolved.getIn()) && StringUtils.isNotBlank(resolved.getName())) {
                pathParams.put(resolved.getName(), resolved);
            }
        }
        return pathParams;
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that URL path template variables ({param}) match declared 'in: path' parameters and have 'required: true'";
    }
}
