package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@LinterFuzzer
@Singleton
public class EmptyResponseSchemaLinterFuzzer extends BaseLinterFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final List<String> ignoreStatusCodes = List.of("204", "304");
    private final List<String> ignoreContentTypes = List.of("application/octet-stream", "image/png", "image/jpeg", "application/pdf");

    public EmptyResponseSchemaLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Detect empty response schemas (inline) that have no properties, $ref, or allOf/anyOf/oneOf");
        testCaseListener.addExpectedResult(log, "All response schemas should define properties, use $ref, or include oneOf/allOf/anyOf");

        List<String> violations = new ArrayList<>();
        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());

        if (operation.getResponses() != null) {
            for (Map.Entry<String, ApiResponse> entry : operation.getResponses().entrySet()) {
                String status = entry.getKey();
                ApiResponse response = entry.getValue();

                if (ignoreStatusCodes.contains(status)) {
                    continue;
                }

                if (response.getContent() != null) {
                    for (Map.Entry<String, MediaType> mediaEntry : response.getContent().entrySet()) {
                        String contentType = mediaEntry.getKey();
                        MediaType media = mediaEntry.getValue();

                        if (ignoreContentTypes.contains(contentType)) {
                            continue;
                        }

                        if (media.getSchema() != null && CatsModelUtils.isEmptyObjectSchema(media.getSchema())) {
                            violations.add("Response schema on response %s is an empty object".formatted(status));
                        }
                    }
                } else {
                    violations.add("Response %s does not define any content".formatted(status));
                }
            }
        }

        if (!violations.isEmpty()) {
            testCaseListener.reportResultWarn(
                    log,
                    data,
                    "Empty response schemas found",
                    String.join("\n", violations)
            );
        } else {
            testCaseListener.reportResultInfo(log, data, "All response schemas define properties, $ref, or structural composition");
        }
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

