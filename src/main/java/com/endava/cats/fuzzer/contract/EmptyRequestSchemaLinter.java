package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Linter
@Singleton
public class EmptyRequestSchemaLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    public EmptyRequestSchemaLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Detect empty request schemas (in components or inline) that have no properties, $ref, or allOf/anyOf/oneOf");
        testCaseListener.addExpectedResult(log, "All request schemas should define properties, use $ref, or include oneOf/allOf/anyOf");

        List<String> violations = new ArrayList<>();
        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());

        RequestBody rb = operation.getRequestBody();

        if (rb != null && rb.getContent() != null) {
            for (Map.Entry<String, MediaType> mediaEntry : rb.getContent().entrySet()) {
                MediaType media = mediaEntry.getValue();
                if (media.getSchema() != null && CatsModelUtils.isEmptyObjectSchema(media.getSchema())) {
                    violations.add("Request schema for media type %s is an empty object".formatted(mediaEntry.getKey()));
                }
            }
        }

        if (!violations.isEmpty()) {
            testCaseListener.reportResultWarn(
                    log,
                    data,
                    "Empty request schemas found",
                    String.join("\n", violations)
            );
        } else {
            testCaseListener.reportResultInfo(log, data, "All request schemas define properties, $ref, or structural composition");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "detects request schemas that define neither properties, $ref, nor structural composition (oneOf, anyOf, allOf)";
    }
}

