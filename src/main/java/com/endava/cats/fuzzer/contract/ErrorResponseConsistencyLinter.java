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
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Verifies that 4xx and 5xx error responses across the OpenAPI contract use a consistent,
 * structured error schema rather than ad-hoc divergent schemas or bare primitives.
 */
@Linter
@Singleton
public class ErrorResponseConsistencyLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new ErrorResponseConsistencyLinter instance.
     *
     * @param tcl the test case listener
     */
    public ErrorResponseConsistencyLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check that all 4xx and 5xx error responses across the contract use a consistent structured schema");
        testCaseListener.addExpectedResult(log, "All 4xx and 5xx responses should use a consistent structured error schema across operations");
        super.addDefaultsForPathAgnosticFuzzers();

        OpenAPI openAPI = data.getOpenApi();
        if (openAPI == null || openAPI.getPaths() == null) {
            testCaseListener.skipTest(log, "No paths found in OpenAPI contract");
            return;
        }

        List<String> violations = analyzeErrorResponses(openAPI);

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All error responses use a consistent structured schema");
        } else {
            testCaseListener.reportResultWarn(log, data, "Inconsistent or unstructured error response schemas found", String.join("\n", violations));
        }
    }

    @SuppressWarnings("rawtypes")
    private List<String> analyzeErrorResponses(OpenAPI openAPI) {
        Map<String, Schema> componentSchemas = OpenApiUtils.getComponentSchemas(openAPI);

        List<String> violations = new ArrayList<>();
        Map<String, List<String>> signatureLocations = new LinkedHashMap<>();
        Map<String, Set<String>> signatureProperties = new LinkedHashMap<>();

        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem item = pathEntry.getValue();
            if (item == null || item.readOperationsMap() == null) {
                continue;
            }
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : item.readOperationsMap().entrySet()) {
                String method = opEntry.getKey().name();
                Operation op = opEntry.getValue();
                if (op == null || op.getResponses() == null) {
                    continue;
                }
                for (Map.Entry<String, ApiResponse> respEntry : op.getResponses().entrySet()) {
                    String status = respEntry.getKey();
                    if (!isErrorStatus(status)) {
                        continue;
                    }
                    ApiResponse response = OpenApiUtils.resolveApiResponse(respEntry.getValue(), openAPI);
                    if (response == null || response.getContent() == null) {
                        continue;
                    }
                    inspectErrorContent(path, method, status, response.getContent(), componentSchemas,
                            signatureLocations, signatureProperties, violations);
                }
            }
        }

        detectDivergentErrorSchemas(signatureLocations, signatureProperties, violations);
        return violations;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void inspectErrorContent(String path, String method, String status,
                                            Map<String, MediaType> content,
                                            Map<String, Schema> componentSchemas,
                                            Map<String, List<String>> signatureLocations,
                                            Map<String, Set<String>> signatureProperties,
                                            List<String> violations) {
        for (Map.Entry<String, MediaType> mediaEntry : content.entrySet()) {
            String contentType = mediaEntry.getKey();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("json")) {
                continue;
            }
            MediaType media = mediaEntry.getValue();
            if (media == null || media.getSchema() == null) {
                continue;
            }
            Schema<?> rawSchema = media.getSchema();
            Schema<?> resolved = CatsModelUtils.resolveSchema(rawSchema, componentSchemas);
            if (resolved == null) {
                continue;
            }
            String endpointDesc = "%s %s (%s)".formatted(method, path, status);
            if (CatsModelUtils.isPrimitiveSchema(resolved)) {
                violations.add("%s defines a primitive '%s' error schema instead of a structured error object"
                        .formatted(endpointDesc, CatsModelUtils.getPrimaryType(resolved)));
                continue;
            }
            Set<String> props = collectPropertyNames(resolved, componentSchemas);
            if (!props.isEmpty()) {
                String signature = String.join(",", props);
                signatureLocations.computeIfAbsent(signature, _ -> new ArrayList<>()).add(endpointDesc);
                signatureProperties.put(signature, props);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Set<String> collectPropertyNames(Schema<?> schema, Map<String, Schema> componentSchemas) {
        Set<String> props = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (schema.getProperties() != null) {
            props.addAll(((Map<String, Schema>) schema.getProperties()).keySet());
        }
        for (Schema<?> sub : CatsModelUtils.getComposedSchemas(schema)) {
            Schema<?> resolvedSub = CatsModelUtils.resolveSchema(sub, componentSchemas);
            if (resolvedSub != null && resolvedSub.getProperties() != null) {
                props.addAll(((Map<String, Schema>) resolvedSub.getProperties()).keySet());
            }
        }
        return props;
    }

    private static void detectDivergentErrorSchemas(Map<String, List<String>> signatureLocations,
                                                    Map<String, Set<String>> signatureProperties,
                                                    List<String> violations) {
        if (signatureLocations.size() <= 1) {
            return;
        }
        String dominantSignature = signatureLocations.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .map(Map.Entry::getKey)
                .orElse(null);
        if (dominantSignature == null) {
            return;
        }
        Set<String> dominantProps = signatureProperties.getOrDefault(dominantSignature, Set.of());
        for (Map.Entry<String, List<String>> entry : signatureLocations.entrySet()) {
            String sig = entry.getKey();
            if (sig.equals(dominantSignature)) {
                continue;
            }
            Set<String> currentProps = signatureProperties.getOrDefault(sig, Set.of());
            boolean sharesCommonErrorProperty = currentProps.stream().anyMatch(dominantProps::contains);
            if (!sharesCommonErrorProperty) {
                violations.add("Error schema with properties [%s] on %s diverges from dominant error schema [%s]"
                        .formatted(sig, entry.getValue(), dominantSignature));
            }
        }
    }

    private static boolean isErrorStatus(String status) {
        return status != null && (status.startsWith("4") || status.startsWith("5") || "default".equalsIgnoreCase(status));
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "error-response-consistency-linter";
    }

    @Override
    public String description() {
        return "verifies that 4xx and 5xx error responses across the contract share a consistent structured error schema";
    }
}
