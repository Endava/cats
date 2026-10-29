package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.fuzzer.contract.util.HttpMethodConsistencyAnalyzer;
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
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies that within each resource group, properties shared between write request schemas
 * (POST, PUT, PATCH) and read response schemas (GET) have consistent types and formats,
 * and that request-only schemas do not mark readOnly properties as required.
 */
@Linter
@Singleton
public class ResourceSchemaSymmetryLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final HttpMethodConsistencyAnalyzer analyzer;

    /**
     * Creates a new ResourceSchemaSymmetryLinter instance.
     *
     * @param tcl      the test case listener
     * @param analyzer the HTTP method consistency analyzer used to group resource paths
     */
    @Inject
    public ResourceSchemaSymmetryLinter(TestCaseListener tcl, HttpMethodConsistencyAnalyzer analyzer) {
        super(tcl);
        this.analyzer = analyzer;
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check that shared properties between write requests (POST/PUT/PATCH) and GET responses have matching types/formats and valid readOnly usage");
        testCaseListener.addExpectedResult(log, "Shared resource properties must keep consistent types and formats across request and response schemas");
        super.addDefaultsForPathAgnosticFuzzers();

        OpenAPI openAPI = data.getOpenApi();
        if (openAPI == null || openAPI.getPaths() == null) {
            testCaseListener.skipTest(log, "No paths found in OpenAPI contract");
            return;
        }

        List<String> violations = analyzeResourceSymmetry(openAPI);

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All resource groups maintain symmetric property types and formats between requests and responses");
        } else {
            testCaseListener.reportResultWarn(log, data, "Resource schema symmetry issues detected", String.join("\n", violations));
        }
    }

    @SuppressWarnings("rawtypes")
    private List<String> analyzeResourceSymmetry(OpenAPI openAPI) {
        Map<String, Schema> componentSchemas = OpenApiUtils.getComponentSchemas(openAPI);

        HttpMethodConsistencyAnalyzer.ResourcePathData resourceData = analyzer.collectResourceData(openAPI);
        List<String> violations = new ArrayList<>();

        for (String group : resourceData.getGroups()) {
            Set<String> groupPaths = resourceData.getPathsForGroup(group);
            Map<String, PropertyTypeInfo> writeProps = new LinkedHashMap<>();
            Map<String, PropertyTypeInfo> readProps = new LinkedHashMap<>();

            for (String path : groupPaths) {
                PathItem item = openAPI.getPaths().get(path);
                if (item == null) {
                    continue;
                }
                collectWriteProperties(path, item, openAPI, componentSchemas, writeProps, violations);
                collectReadProperties(item, analyzer.isItemPath(path), openAPI, componentSchemas, readProps);
            }

            for (Map.Entry<String, PropertyTypeInfo> writeEntry : writeProps.entrySet()) {
                String propName = writeEntry.getKey();
                PropertyTypeInfo writeInfo = writeEntry.getValue();
                PropertyTypeInfo readInfo = readProps.get(propName);
                if (readInfo != null && !writeInfo.isCompatibleWith(readInfo)) {
                    violations.add("Group [%s] property '%s' has mismatched type/format between write request (%s) and GET response (%s)"
                            .formatted(group, propName, writeInfo.describe(), readInfo.describe()));
                }
            }
        }

        return violations;
    }

    @SuppressWarnings("rawtypes")
    private static void collectWriteProperties(String path, PathItem item, OpenAPI openAPI,
                                               Map<String, Schema> componentSchemas,
                                               Map<String, PropertyTypeInfo> writeProps,
                                               List<String> violations) {
        List<Operation> writeOps = new ArrayList<>();
        if (item.getPost() != null) {
            writeOps.add(item.getPost());
        }
        if (item.getPut() != null) {
            writeOps.add(item.getPut());
        }
        if (item.getPatch() != null) {
            writeOps.add(item.getPatch());
        }

        for (Operation op : writeOps) {
            RequestBody rb = OpenApiUtils.resolveRequestBody(op.getRequestBody(), openAPI);
            if (rb == null || rb.getContent() == null) {
                continue;
            }
            for (MediaType media : rb.getContent().values()) {
                if (media == null || media.getSchema() == null) {
                    continue;
                }
                Schema<?> raw = media.getSchema();
                Schema<?> resolved = CatsModelUtils.resolveSchema(raw, componentSchemas);
                if (resolved == null) {
                    continue;
                }
                if (raw.get$ref() == null) {
                    checkInlineRequiredReadOnly(path, resolved, violations);
                }
                extractProperties(resolved, componentSchemas, writeProps);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void checkInlineRequiredReadOnly(String path, Schema<?> schema, List<String> violations) {
        if (schema.getRequired() == null || schema.getProperties() == null) {
            return;
        }
        Map<String, Schema> props = schema.getProperties();
        for (String reqProp : schema.getRequired()) {
            Schema<?> propSchema = props.get(reqProp);
            if (propSchema != null && Boolean.TRUE.equals(propSchema.getReadOnly())) {
                violations.add("Path '%s' inline request schema marks readOnly property '%s' as required"
                        .formatted(path, reqProp));
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void collectReadProperties(PathItem item, boolean isItemPath, OpenAPI openAPI,
                                              Map<String, Schema> componentSchemas,
                                              Map<String, PropertyTypeInfo> readProps) {
        Operation getOp = item.getGet();
        if (getOp == null || getOp.getResponses() == null) {
            return;
        }
        for (Map.Entry<String, ApiResponse> entry : getOp.getResponses().entrySet()) {
            if (entry.getKey() == null || !entry.getKey().startsWith("2")) {
                continue;
            }
            ApiResponse resp = OpenApiUtils.resolveApiResponse(entry.getValue(), openAPI);
            if (resp == null || resp.getContent() == null) {
                continue;
            }
            for (MediaType media : resp.getContent().values()) {
                if (media == null || media.getSchema() == null) {
                    continue;
                }
                Schema<?> resolved = CatsModelUtils.resolveSchema(media.getSchema(), componentSchemas);
                if (CatsModelUtils.isArraySchema(resolved) && resolved.getItems() != null) {
                    resolved = CatsModelUtils.resolveSchema(resolved.getItems(), componentSchemas);
                    if (resolved != null) {
                        extractProperties(resolved, componentSchemas, readProps);
                    }
                } else if (isItemPath && resolved != null) {
                    extractProperties(resolved, componentSchemas, readProps);
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void extractProperties(Schema<?> schema, Map<String, Schema> componentSchemas,
                                          Map<String, PropertyTypeInfo> target) {
        extractProperties(schema, componentSchemas, target, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void extractProperties(Schema<?> schema, Map<String, Schema> componentSchemas,
                                          Map<String, PropertyTypeInfo> target, Set<Schema<?>> visited) {
        if (schema == null || !visited.add(schema)) {
            return;
        }
        if (schema.getProperties() != null) {
            for (Map.Entry<String, Schema> entry : ((Map<String, Schema>) schema.getProperties()).entrySet()) {
                Schema<?> propSchema = CatsModelUtils.resolveSchema(entry.getValue(), componentSchemas);
                String primaryType = CatsModelUtils.getPrimaryType(propSchema);
                if (propSchema != null && primaryType != null) {
                    target.putIfAbsent(entry.getKey(), new PropertyTypeInfo(
                            primaryType.toLowerCase(Locale.ROOT),
                            propSchema.getFormat() != null ? propSchema.getFormat().toLowerCase(Locale.ROOT) : null));
                }
            }
        }
        for (Schema<?> sub : CatsModelUtils.getComposedSchemas(schema)) {
            Schema<?> resolvedSub = CatsModelUtils.resolveSchema(sub, componentSchemas);
            if (resolvedSub != null) {
                extractProperties(resolvedSub, componentSchemas, target, visited);
            }
        }
    }

    private record PropertyTypeInfo(String type, String format) {
        boolean isCompatibleWith(PropertyTypeInfo other) {
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            if (this.format == null || other.format == null) {
                return true;
            }
            return Objects.equals(this.format, other.format);
        }

        String describe() {
            return format == null ? type : type + ":" + format;
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "resource-schema-symmetry-linter";
    }

    @Override
    public String description() {
        return "verifies that properties shared between write request schemas (POST/PUT/PATCH) and GET response schemas within a resource group have consistent types and formats";
    }
}
