package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.OpenApiUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Verifies that path variables across the OpenAPI contract use consistent names for the same
 * resource hierarchy segment and consistent schema types/formats across operations.
 */
@Linter
@Singleton
public class PathVariableConsistencyLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new PathVariableConsistencyLinter instance.
     *
     * @param tcl the test case listener
     */
    public PathVariableConsistencyLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check that path variables use consistent names per resource segment and consistent types/formats across operations");
        testCaseListener.addExpectedResult(log, "Each resource segment should use a single path variable name, and each path variable name should keep a consistent type and format");
        super.addDefaultsForPathAgnosticFuzzers();

        OpenAPI openAPI = data.getOpenApi();
        if (openAPI == null || openAPI.getPaths() == null) {
            testCaseListener.skipTest(log, "No paths found in OpenAPI contract");
            return;
        }

        List<String> violations = analyzePathVariables(openAPI);

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All path variables use consistent names and schema types/formats across the contract");
        } else {
            testCaseListener.reportResultWarn(log, data, "Inconsistent path variable definitions found", String.join("\n", violations));
        }
    }

    @SuppressWarnings("rawtypes")
    private static List<String> analyzePathVariables(OpenAPI openAPI) {
        Map<String, Set<String>> segmentToVarNames = new LinkedHashMap<>();
        Map<String, Set<String>> varNameToSignatures = new LinkedHashMap<>();
        Map<String, Schema> componentSchemas = OpenApiUtils.getComponentSchemas(openAPI);

        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            if (path == null || pathItem == null) {
                continue;
            }
            collectSegmentVariableNames(path, segmentToVarNames);
            collectParameterTypeSignatures(pathItem, openAPI, componentSchemas, varNameToSignatures);
        }

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : segmentToVarNames.entrySet()) {
            if (entry.getValue().size() > 1) {
                violations.add("Resource segment '/%s' uses inconsistent path variable names across paths: %s"
                        .formatted(entry.getKey(), entry.getValue()));
            }
        }

        for (Map.Entry<String, Set<String>> entry : varNameToSignatures.entrySet()) {
            if (entry.getValue().size() > 1) {
                violations.add("Path parameter '{%s}' is declared with inconsistent type/format across operations: %s"
                        .formatted(entry.getKey(), entry.getValue()));
            }
        }

        return violations;
    }

    private static void collectSegmentVariableNames(String path, Map<String, Set<String>> segmentToVarNames) {
        String[] segments = path.split("/", -1);
        for (int i = 1; i < segments.length; i++) {
            String current = segments[i];
            String previous = segments[i - 1];
            if (OpenApiUtils.isAPathVariable(current) && StringUtils.isNotBlank(previous) && OpenApiUtils.isNotAPathVariable(previous)) {
                String varName = current.substring(1, current.length() - 1);
                segmentToVarNames.computeIfAbsent(previous, _ -> new LinkedHashSet<>()).add(varName);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void collectParameterTypeSignatures(PathItem pathItem, OpenAPI openAPI,
                                                       Map<String, Schema> componentSchemas,
                                                       Map<String, Set<String>> varNameToSignatures) {
        if (pathItem.readOperations() == null) {
            return;
        }
        for (Operation op : pathItem.readOperations()) {
            for (Parameter param : OpenApiUtils.collectPathAndOperationParameters(pathItem, op, openAPI)) {
                if (!"path".equalsIgnoreCase(param.getIn()) || StringUtils.isBlank(param.getName())) {
                    continue;
                }
                Schema<?> resolvedSchema = CatsModelUtils.resolveSchema(param.getSchema(), componentSchemas);
                String primaryType = CatsModelUtils.getPrimaryType(resolvedSchema);
                if (resolvedSchema != null && StringUtils.isNotBlank(primaryType)) {
                    String type = primaryType.toLowerCase(Locale.ROOT);
                    String format = StringUtils.isNotBlank(resolvedSchema.getFormat())
                            ? ":" + resolvedSchema.getFormat().toLowerCase(Locale.ROOT)
                            : "";
                    varNameToSignatures.computeIfAbsent(param.getName(), _ -> new LinkedHashSet<>()).add(type + format);
                }
            }
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "path-variable-consistency-linter";
    }

    @Override
    public String description() {
        return "verifies that path variables use consistent names per resource segment and consistent schema types/formats across all operations";
    }
}
