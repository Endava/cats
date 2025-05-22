package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@LinterFuzzer
@Singleton
public class MultipleSuccessCodesLinterFuzzer extends BaseLinterFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    public MultipleSuccessCodesLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if an operation defines more than one success (2xx) status code");
        testCaseListener.addExpectedResult(log, "Each operation should ideally define a single 2xx response for clarity and predictability");

        List<String> violations = new ArrayList<>();
        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());

        if (operation.getResponses() != null) {
            List<String> successCodes = operation.getResponses().keySet().stream()
                    .filter(code -> code.startsWith("2"))
                    .toList();

            if (successCodes.size() > 1) {
                violations.add("Operation defines multiple 2xx status codes: %s"
                        .formatted(successCodes));
            }
        }

        if (!violations.isEmpty()) {
            testCaseListener.reportResultWarn(
                    log,
                    data,
                    "Multiple 2xx success status codes found",
                    String.join("\n", violations)
            );
        } else {
            testCaseListener.reportResultInfo(log, data, "Each operation defines at most one success (2xx) response code");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "flags operations that define more than one 2xx success response, which may cause ambiguity for SDKs and clients";
    }
}
