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
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Linter
@Singleton
public class EmptyResponseSchemaLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private static final Set<String> IGNORE_STATUS_CODES = Set.of("204", "205", "301", "302", "303", "304", "307", "308");
    private static final Set<String> LOCATION_ALLOWED_BODYLESS_CODES = Set.of("201", "202");
    private final List<String> ignoreContentTypes = List.of("application/octet-stream", "image/png", "image/jpeg", "application/pdf", "text/event-stream");

    public EmptyResponseSchemaLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Detect empty response schemas (inline) that have no properties, $ref, or allOf/anyOf/oneOf");
        testCaseListener.addExpectedResult(log, "All response schemas should define properties, use $ref, or include oneOf/allOf/anyOf");

        List<String> violations = collectViolations(data);

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All response schemas define properties, $ref, or structural composition");
        } else {
            testCaseListener.reportResultWarn(log, data, "Empty response schemas found", String.join("\n", violations));
        }
    }

    private List<String> collectViolations(FuzzingData data) {
        List<String> violations = new ArrayList<>();
        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());

        if (operation == null || operation.getResponses() == null) {
            return violations;
        }

        for (Map.Entry<String, ApiResponse> entry : operation.getResponses().entrySet()) {
            processResponseEntry(violations, entry.getKey(), entry.getValue(), data);
        }

        return violations;
    }

    private void processResponseEntry(List<String> violations, String status, ApiResponse response, FuzzingData data) {
        if (status != null && (IGNORE_STATUS_CODES.contains(status) || status.startsWith("1"))) {
            return;
        }

        ApiResponse resolvedResponse = OpenApiUtils.resolveApiResponse(response, data.getOpenApi());
        if (resolvedResponse == null || resolvedResponse.get$ref() != null) {
            return;
        }

        if (resolvedResponse.getContent() == null) {
            if (LOCATION_ALLOWED_BODYLESS_CODES.contains(status) && hasLocationHeader(resolvedResponse)) {
                return;
            }
            violations.add("Response %s does not define any content".formatted(status));
            return;
        }

        processMediaTypes(violations, status, resolvedResponse.getContent(), data);
    }

    private static boolean hasLocationHeader(ApiResponse response) {
        return response.getHeaders() != null
                && response.getHeaders().keySet().stream().anyMatch("Location"::equalsIgnoreCase);
    }

    @SuppressWarnings("rawtypes")
    private void processMediaTypes(List<String> violations, String status, Map<String, MediaType> content, FuzzingData data) {
        Map<String, Schema> schemas = OpenApiUtils.getComponentSchemas(data.getOpenApi());

        for (Map.Entry<String, MediaType> mediaEntry : content.entrySet()) {
            String contentType = mediaEntry.getKey();
            MediaType media = mediaEntry.getValue();

            if (isIgnoredContentType(contentType)) {
                continue;
            }

            Schema<?> schema = media != null ? CatsModelUtils.resolveSchema(media.getSchema(), schemas) : null;
            if (schema != null && CatsModelUtils.isEmptyObjectSchema(schema)) {
                violations.add("Response schema on response %s is an empty object".formatted(status));
            }
        }
    }

    private boolean isIgnoredContentType(String contentType) {
        return contentType != null && (ignoreContentTypes.contains(contentType)
                || contentType.startsWith("image/")
                || contentType.startsWith("audio/")
                || contentType.startsWith("video/"));
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "detects response schemas that define neither properties, $ref, nor structural composition (oneOf, anyOf, allOf)";
    }
}