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
import io.swagger.v3.oas.models.parameters.RequestBody;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flags request body object schemas that explicitly allow arbitrary unconstrained
 * additionalProperties (additionalProperties: true or an empty/free-form schema).
 */
@Linter
@Singleton
public class UnconstrainedAdditionalPropertiesLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new UnconstrainedAdditionalPropertiesLinter instance.
     *
     * @param tcl the test case listener
     */
    public UnconstrainedAdditionalPropertiesLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if request body schemas for {} {} allow unconstrained additionalProperties", data.getMethod(), data.getPath());
        testCaseListener.addExpectedResult(log, "Request body object schemas should not enable unconstrained additionalProperties (true or free-form schema)");

        Operation operation = data.getPathItem() != null && data.getMethod() != null
                ? HttpMethod.getOperation(data.getMethod(), data.getPathItem())
                : null;
        if (operation == null) {
            testCaseListener.skipTest(log, "Operation not present");
            return;
        }

        RequestBody rb = OpenApiUtils.resolveRequestBody(operation.getRequestBody(), data.getOpenApi());
        if (rb == null || rb.getContent() == null) {
            testCaseListener.reportResultInfo(log, data, "No unconstrained additionalProperties found in request body schemas");
            return;
        }

        Map<String, Schema> componentSchemas = OpenApiUtils.getComponentSchemas(data.getOpenApi());
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, MediaType> entry : rb.getContent().entrySet()) {
            MediaType media = entry.getValue();
            if (media != null && media.getSchema() != null) {
                Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                checkSchemaRecursive(media.getSchema(), entry.getKey(), componentSchemas, visited, violations);
            }
        }

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "No unconstrained additionalProperties found in request body schemas");
        } else {
            testCaseListener.reportResultWarn(log, data, "Unconstrained additionalProperties in request schema", String.join("\n", violations));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void checkSchemaRecursive(Schema<?> schema, String pathContext,
                                             Map<String, Schema> componentSchemas,
                                             Set<Schema<?>> visited, List<String> violations) {
        Schema<?> resolved = CatsModelUtils.resolveSchema(schema, componentSchemas);
        if (resolved == null || !visited.add(resolved)) {
            return;
        }

        Object addProps = resolved.getAdditionalProperties();
        if (Boolean.TRUE.equals(addProps)
                || (addProps instanceof Schema<?> apSchema && CatsModelUtils.isFreeFormSchema(apSchema))) {
            violations.add("Request schema '%s' allows unconstrained additionalProperties".formatted(pathContext));
        }

        if (resolved.getProperties() != null) {
            for (Map.Entry<String, Schema> prop : ((Map<String, Schema>) resolved.getProperties()).entrySet()) {
                checkSchemaRecursive(prop.getValue(), pathContext + "." + prop.getKey(), componentSchemas, visited, violations);
            }
        }
        if (resolved.getItems() != null) {
            checkSchemaRecursive(resolved.getItems(), pathContext + "[]", componentSchemas, visited, violations);
        }
        for (Schema<?> sub : CatsModelUtils.getComposedSchemas(resolved)) {
            checkSchemaRecursive(sub, pathContext, componentSchemas, visited, violations);
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "flags request body object schemas that explicitly allow unconstrained additionalProperties (true or an untyped free-form schema)";
    }
}
