package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Linter
@Singleton
public class ArrayWithoutItemsLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    public ArrayWithoutItemsLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Detect array schemas (inline or referenced) that do not define an 'items' schema");
        testCaseListener.addExpectedResult(log, "All array schemas should define an 'items' schema indicating the type of elements");

        List<String> violations = new ArrayList<>();
        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());

        checkSchemas(operation.getRequestBody() != null ? operation.getRequestBody().getContent() : null,
                "Request", violations);

        if (operation.getResponses() != null) {
            for (Map.Entry<String, io.swagger.v3.oas.models.responses.ApiResponse> resp : operation.getResponses().entrySet()) {
                checkSchemas(resp.getValue().getContent(), "Response %s".formatted(resp.getKey()), violations);
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

    private void checkSchemas(Content content, String context, List<String> violations) {
        if (content == null) {
            return;
        }
        for (Map.Entry<String, MediaType> media : content.entrySet()) {
            Schema<?> schema = media.getValue().getSchema();
            if (CatsModelUtils.isArraySchema(schema) && schema.getItems() == null) {
                violations.add("%s content-type '%s' has array schema without 'items' definition"
                        .formatted(context, media.getKey()));
            }
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
