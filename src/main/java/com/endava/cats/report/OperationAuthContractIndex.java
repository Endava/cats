package com.endava.cats.report;

import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Builds a per-operation lookup table of {@link OperationAuthContract} entries from an {@link OpenAPI} specification,
 * keyed by {@link WfcReportBuilder#toOperationId(String, String)}.
 */
public final class OperationAuthContractIndex {

    private OperationAuthContractIndex() {
        // Utility class
    }

    /**
     * Indexes all documented operations in {@code openAPI}.
     *
     * @param openAPI the parsed OpenAPI specification (may be {@code null})
     * @return an unmodifiable map from operation ID ({@code VERB:/path}) to its {@link OperationAuthContract}
     */
    public static Map<String, OperationAuthContract> from(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getPaths() == null) {
            return Map.of();
        }
        boolean hasSecuritySchemes = CatsModelUtils.hasSecuritySchemes(openAPI);
        Map<String, OperationAuthContract> index = new LinkedHashMap<>();
        openAPI.getPaths().forEach((path, pathItem) -> indexPathItem(path, pathItem, hasSecuritySchemes, index));
        return Map.copyOf(index);
    }

    private static void indexPathItem(String path, PathItem pathItem, boolean hasSecuritySchemes,
                                      Map<String, OperationAuthContract> index) {
        if (pathItem == null || pathItem.readOperationsMap() == null) {
            return;
        }
        pathItem.readOperationsMap().forEach((verb, operation) ->
                index.put(WfcReportBuilder.toOperationId(verb.name(), path), toContract(operation, hasSecuritySchemes)));
    }

    private static OperationAuthContract toContract(Operation operation, boolean hasSecuritySchemes) {
        Set<String> responseCodes = operation != null && operation.getResponses() != null
                ? operation.getResponses().keySet()
                : Collections.emptySet();
        return new OperationAuthContract(
                hasSecuritySchemes,
                CatsModelUtils.isExplicitlyPublic(operation),
                CatsModelUtils.declares401(responseCodes));
    }
}
