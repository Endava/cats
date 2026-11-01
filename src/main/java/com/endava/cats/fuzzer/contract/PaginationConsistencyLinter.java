package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.fuzzer.contract.util.LinterVocabulary;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.OpenApiUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Verifies that paginated GET operations across the contract use a consistent pagination
 * parameter naming convention and return pagination metadata rather than a bare JSON array.
 */
@Linter
@Singleton
public class PaginationConsistencyLinter extends BaseLinter {
    private static final Set<String> PAGINATION_HEADER_MARKERS = Set.of(
            "xtotalcount", "totalcount", "xpagination", "link", "xnextpage", "nextpage"
    );

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new PaginationConsistencyLinter instance.
     *
     * @param tcl the test case listener
     */
    public PaginationConsistencyLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check that paginated GET operations use consistent pagination query parameter names and return pagination metadata");
        testCaseListener.addExpectedResult(log, "Paginated GET operations should use consistent pagination parameter names across the contract and wrap results in a pagination envelope or headers");
        super.addDefaultsForPathAgnosticFuzzers();

        OpenAPI openAPI = data.getOpenApi();
        if (openAPI == null || openAPI.getPaths() == null) {
            testCaseListener.skipTest(log, "No paths found in OpenAPI contract");
            return;
        }

        List<String> violations = analyzePaginationConsistency(openAPI);

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All paginated GET operations use consistent parameter conventions and structured responses");
        } else {
            testCaseListener.reportResultWarn(log, data, "Pagination consistency issues detected", String.join("\n", violations));
        }
    }

    @SuppressWarnings("rawtypes")
    private static List<String> analyzePaginationConsistency(OpenAPI openAPI) {
        Map<String, Schema> componentSchemas = OpenApiUtils.getComponentSchemas(openAPI);
        Map<String, List<String>> paramSignatureToEndpoints = new LinkedHashMap<>();
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            PathItem pathItem = entry.getValue();
            if (pathItem != null && pathItem.getGet() != null) {
                analyzePaginatedGet(entry.getKey(), pathItem, openAPI, componentSchemas, paramSignatureToEndpoints, violations);
            }
        }

        if (paramSignatureToEndpoints.size() > 1) {
            StringBuilder mismatch = new StringBuilder("Inconsistent pagination query parameter sets across endpoints: ");
            paramSignatureToEndpoints.forEach((sig, endpoints) ->
                    mismatch.append("[").append(sig).append("] on ").append(endpoints).append("; "));
            violations.add(mismatch.toString().stripTrailing());
        }

        return violations;
    }

    @SuppressWarnings("rawtypes")
    private static void analyzePaginatedGet(String path, PathItem pathItem, OpenAPI openAPI, Map<String, Schema> componentSchemas,
                                            Map<String, List<String>> paramSignatureToEndpoints, List<String> violations) {
        Operation getOp = pathItem.getGet();
        Set<String> paginationParams = extractPaginationQueryParams(pathItem, getOp, openAPI);
        if (paginationParams.isEmpty()) {
            return;
        }

        String signature = String.join(",", paginationParams);
        paramSignatureToEndpoints.computeIfAbsent(signature, _ -> new ArrayList<>()).add("GET " + path);

        if (returnsBareArrayWithoutPaginationHeaders(getOp, openAPI, componentSchemas)) {
            violations.add("GET %s declares pagination parameters %s but its 2xx response is a bare array without pagination metadata".formatted(path, paginationParams));
        }
    }

    private static Set<String> extractPaginationQueryParams(PathItem pathItem, Operation getOp, OpenAPI openAPI) {
        Set<String> found = new TreeSet<>();
        for (Parameter param : OpenApiUtils.collectPathAndOperationParameters(pathItem, getOp, openAPI)) {
            if ("query".equalsIgnoreCase(param.getIn()) && param.getName() != null) {
                String normalized = LinterVocabulary.normalizeParamName(param.getName());
                if (LinterVocabulary.PAGINATION_PARAMS.contains(normalized)) {
                    found.add(param.getName());
                }
            }
        }
        return found;
    }

    @SuppressWarnings("rawtypes")
    private static boolean returnsBareArrayWithoutPaginationHeaders(Operation getOp, OpenAPI openAPI,
                                                                     Map<String, Schema> componentSchemas) {
        if (getOp.getResponses() == null) {
            return false;
        }
        for (Map.Entry<String, ApiResponse> entry : getOp.getResponses().entrySet()) {
            ApiResponse resolvedResp = resolveSuccessResponseWithContent(entry.getKey(), entry.getValue(), openAPI);
            if (resolvedResp != null) {
                if (hasPaginationHeaders(resolvedResp)) {
                    return false;
                }
                if (hasArrayMediaSchema(resolvedResp, componentSchemas)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ApiResponse resolveSuccessResponseWithContent(String status, ApiResponse response, OpenAPI openAPI) {
        if (status == null || !status.startsWith("2")) {
            return null;
        }
        ApiResponse resolvedResp = OpenApiUtils.resolveApiResponse(response, openAPI);
        return resolvedResp == null || resolvedResp.getContent() == null ? null : resolvedResp;
    }

    @SuppressWarnings("rawtypes")
    private static boolean hasArrayMediaSchema(ApiResponse response, Map<String, Schema> componentSchemas) {
        for (MediaType media : response.getContent().values()) {
            if (media != null && media.getSchema() != null
                    && CatsModelUtils.isArraySchema(CatsModelUtils.resolveSchema(media.getSchema(), componentSchemas))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPaginationHeaders(ApiResponse response) {
        if (response.getHeaders() == null || response.getHeaders().isEmpty()) {
            return false;
        }
        return response.getHeaders().keySet().stream()
                .map(h -> h.replaceAll("[-_]", "").toLowerCase(Locale.ROOT))
                .anyMatch(PAGINATION_HEADER_MARKERS::contains);
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "pagination-consistency-linter";
    }

    @Override
    public String description() {
        return "verifies that paginated GET operations across the contract use consistent pagination parameter names and return pagination metadata rather than a bare array";
    }
}
