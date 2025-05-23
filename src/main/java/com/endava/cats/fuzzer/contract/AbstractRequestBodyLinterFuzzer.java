package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;

import java.util.Map;

public abstract class AbstractRequestBodyLinterFuzzer extends BaseLinterFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    protected AbstractRequestBodyLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    protected abstract HttpMethod targetHttpMethod();

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if %s methods does not have a body".formatted(targetHttpMethod()));
        testCaseListener.addExpectedResult(log, "%s methods must define a requestBody with a valid schema".formatted(targetHttpMethod()));

        Operation op = HttpMethod.getOperation(targetHttpMethod(), data.getPathItem());
        if (op == null) {
            testCaseListener.skipTest(log, "%s method not present".formatted(targetHttpMethod()));
            return;
        }

        RequestBody body = op.getRequestBody();
        boolean missing = true;

        if (body != null && body.getContent() != null) {
            for (Map.Entry<String, MediaType> entry : body.getContent().entrySet()) {
                if (entry.getValue() != null && entry.getValue().getSchema() != null) {
                    missing = false;
                    break;
                }
            }
        }

        super.addDefaultsForPathAgnosticFuzzers();

        if (missing) {
            testCaseListener.reportResultWarn(
                    log,
                    data,
                    "%s without request body".formatted(targetHttpMethod()),
                    "%s method should have a body".formatted(targetHttpMethod())
            );
        } else {
            testCaseListener.reportResultInfo(log, data, "%s method does have a body".formatted(targetHttpMethod()));
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath();
    }


    @Override
    public String description() {
        return "verifies that all %s operations define a request body schema (not null or empty)".formatted(targetHttpMethod());
    }
}
