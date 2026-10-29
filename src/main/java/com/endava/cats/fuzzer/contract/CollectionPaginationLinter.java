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
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Linter
@Singleton
public class CollectionPaginationLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    private static final Set<String> PAGINATION_PARAMS = Set.of(
            "limit", "offset", "page", "perpage", "pagesize", "pagenumber", "pagetoken",
            "size", "cursor", "startafter", "after", "before", "next", "nexttoken",
            "continuationtoken", "token", "skip", "take", "top"
    );

    private static final Set<String> NON_COLLECTION_SEGMENTS = Set.of(
            "me", "self", "current", "resolved", "health", "healthz", "ready", "readyz",
            "live", "livez", "status", "info", "version", "ping", "count", "summary",
            "check", "name-check", "validate", "verify", "exists", "config", "configuration",
            "profile", "context", "login", "logout", "export", "download", "metrics", "stats"
    );

    private static final Set<String> ENVELOPE_ARRAY_PROPERTIES = Set.of(
            "items", "data", "results", "content", "records", "elements", "values", "entries", "list", "resources"
    );

    public CollectionPaginationLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        if (data.getMethod() != HttpMethod.GET || isItemPath(data.getPath()) || !isCollectionEndpoint(data)) {
            testCaseListener.skipTest(log, "Skipping pagination check for non-GET operations");
            return;
        }

        testCaseListener.addScenario(log, "Check if GET operations on collection endpoints support pagination parameters");
        testCaseListener.addExpectedResult(log, "Collection GETs should define pagination query parameters like limit, offset, page, size, or cursor");

        List<String> violations = new ArrayList<>();

        Operation getOp = data.getPathItem().getGet();

        Set<String> queryParams = collectQueryParams(data, data.getPathItem(), getOp);

        boolean hasPagination = queryParams.stream()
                .map(p -> p.replaceAll("[-_]", "").toLowerCase(Locale.ROOT))
                .anyMatch(PAGINATION_PARAMS::contains);

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

    private Set<String> collectQueryParams(FuzzingData data, PathItem pathItem, Operation getOp) {
        Set<String> names = new HashSet<>();
        if (data.getQueryParams() != null) {
            names.addAll(data.getQueryParams());
        }

        names.addAll(OpenApiUtils.collectPathAndOperationParameters(pathItem, getOp, data.getOpenApi())
                .stream()
                .filter(p -> "query".equalsIgnoreCase(p.getIn()) && p.getName() != null)
                .map(Parameter::getName)
                .collect(Collectors.toSet()));
        return names;
    }

    @SuppressWarnings("rawtypes")
    private boolean isCollectionEndpoint(FuzzingData data) {
        if (data.getPath() != null) {
            String lastSegment = data.getPath().substring(data.getPath().lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            if (NON_COLLECTION_SEGMENTS.contains(lastSegment)) {
                return false;
            }
        }
        if (data.getPathItem() == null || data.getPathItem().getGet() == null) {
            return true;
        }
        Operation getOp = data.getPathItem().getGet();
        if (getOp.getResponses() == null || getOp.getResponses().isEmpty()) {
            return true;
        }
        List<ApiResponse> twoXxResponses = getOp.getResponses().entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().startsWith("2") && e.getValue() != null)
                .map(Map.Entry::getValue)
                .toList();
        if (twoXxResponses.isEmpty()) {
            return false;
        }

        Map<String, Schema> schemas = OpenApiUtils.getComponentSchemas(data.getOpenApi());

        boolean anySchemaPresent = false;
        String lastSegment = data.getPath() != null
                ? data.getPath().substring(data.getPath().lastIndexOf('/') + 1).replaceAll("[-_]", "").toLowerCase(Locale.ROOT)
                : "";
        for (ApiResponse resp : twoXxResponses) {
            if (resp.getContent() == null) {
                continue;
            }
            for (MediaType media : resp.getContent().values()) {
                if (media == null || media.getSchema() == null) {
                    continue;
                }
                anySchemaPresent = true;
                Schema<?> resolved = CatsModelUtils.resolveSchema(media.getSchema(), schemas);
                if (isArrayOrPaginatedEnvelope(resolved, schemas, lastSegment)) {
                    return true;
                }
            }
        }
        return !anySchemaPresent;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isArrayOrPaginatedEnvelope(Schema<?> schema, Map<String, Schema> schemas, String lastSegment) {
        if (schema == null) {
            return false;
        }
        if (CatsModelUtils.isArraySchema(schema)) {
            return true;
        }
        if (schema.getProperties() != null) {
            for (Map.Entry<String, Schema> prop : ((Map<String, Schema>) schema.getProperties()).entrySet()) {
                String propName = prop.getKey().replaceAll("[-_]", "").toLowerCase(Locale.ROOT);
                Schema<?> propSchema = CatsModelUtils.resolveSchema(prop.getValue(), schemas);
                if (CatsModelUtils.isArraySchema(propSchema)
                        && (ENVELOPE_ARRAY_PROPERTIES.contains(propName) || (!lastSegment.isEmpty() && propName.equals(lastSegment)))) {
                    return true;
                }
            }
        }
        for (Schema<?> composed : CatsModelUtils.getComposedSchemas(schema)) {
            if (isArrayOrPaginatedEnvelope(CatsModelUtils.resolveSchema(composed, schemas), schemas, lastSegment)) {
                return true;
            }
        }
        return false;
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