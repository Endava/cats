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
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Linter
@Singleton
public class ArrayWithoutItemsLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    public ArrayWithoutItemsLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Detect array schemas (inline or referenced) that do not define an 'items' schema");
        testCaseListener.addExpectedResult(log, "All array schemas should define an 'items' schema indicating the type of elements");

        List<String> violations = new ArrayList<>();
        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());
        if (operation == null) {
            testCaseListener.skipTest(log, "%s method not present".formatted(data.getMethod()));
            return;
        }

        Map<String, Schema> componentSchemas = OpenApiUtils.getComponentSchemas(data.getOpenApi());

        checkSchemas(operation.getRequestBody() != null ? operation.getRequestBody().getContent() : null,
                "Request", componentSchemas, violations);

        if (operation.getResponses() != null) {
            for (Map.Entry<String, ApiResponse> resp : operation.getResponses().entrySet()) {
                checkSchemas(resp.getValue().getContent(), "Response %s".formatted(resp.getKey()), componentSchemas, violations);
            }
        }

        if (!violations.isEmpty()) {
            testCaseListener.reportResultWarn(
                    log,
                    data,
                    "Array schemas missing 'items' definitions",
                    String.join("\n", violations)
            );
        } else {
            testCaseListener.reportResultInfo(log, data, "All array schemas define an 'items' schema");
        }
    }

    @SuppressWarnings("rawtypes")
    private void checkSchemas(Content content, String context, Map<String, Schema> componentSchemas, List<String> violations) {
        if (content == null) {
            return;
        }
        for (Map.Entry<String, MediaType> media : content.entrySet()) {
            if (media.getValue() == null) {
                continue;
            }
            Schema<?> schema = media.getValue().getSchema();
            String mediaContext = "%s content-type '%s'".formatted(context, media.getKey());
            Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            checkSchemaRecursive(schema, mediaContext, null, componentSchemas, visited, violations);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void checkSchemaRecursive(Schema<?> schema, String mediaContext, String propertyPath,
                                      Map<String, Schema> componentSchemas, Set<Schema<?>> visited, List<String> violations) {
        Schema<?> resolved = CatsModelUtils.resolveSchema(schema, componentSchemas);
        if (resolved == null || !visited.add(resolved)) {
            return;
        }
        if (CatsModelUtils.isArraySchema(resolved)) {
            if (resolved.getItems() == null) {
                if (propertyPath == null) {
                    violations.add("%s has array schema without 'items' definition".formatted(mediaContext));
                } else {
                    violations.add("%s property '%s' has array schema without 'items' definition".formatted(mediaContext, propertyPath));
                }
            } else {
                checkSchemaRecursive(resolved.getItems(), mediaContext,
                        propertyPath == null ? "[]" : propertyPath + "[]", componentSchemas, visited, violations);
            }
        }
        if (resolved.getProperties() != null) {
            for (Map.Entry<String, Schema> prop : ((Map<String, Schema>) resolved.getProperties()).entrySet()) {
                String nextPath = propertyPath == null ? prop.getKey() : propertyPath + "." + prop.getKey();
                checkSchemaRecursive(prop.getValue(), mediaContext, nextPath, componentSchemas, visited, violations);
            }
        }
        for (Schema<?> subSchema : CatsModelUtils.getComposedSchemas(resolved)) {
            checkSchemaRecursive(subSchema, mediaContext, propertyPath, componentSchemas, visited, violations);
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "detects array schemas that do not define an 'items' property, which results in ambiguous data structures for arrays";
    }
}
