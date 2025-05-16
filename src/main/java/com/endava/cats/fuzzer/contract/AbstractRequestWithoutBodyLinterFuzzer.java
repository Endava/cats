package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

import java.util.List;
import java.util.Optional;

public abstract class AbstractRequestWithoutBodyLinterFuzzer extends BaseLinterFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new instance of subclasses.
     *
     * @param tcl the test case listener
     */
    protected AbstractRequestWithoutBodyLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    protected abstract HttpMethod targetHttpMethod();


    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if %s methods have a body".formatted(targetHttpMethod()));
        testCaseListener.addExpectedResult(log, "%s methods must not have a body".formatted(targetHttpMethod()));

        Operation operation = getOperation(data.getPathItem());
        if (operation == null) {
            testCaseListener.skipTest(log, "%s method not present".formatted(targetHttpMethod()));
            return;
        }

        boolean hasRequestBodyInRoot = operation.getRequestBody() != null;
        boolean hasRequestBodyInParameters = Optional.ofNullable(operation.getParameters())
                .orElse(List.of())
                .stream().anyMatch(param -> param.getName().equals("body"));

        if (hasRequestBodyInRoot || hasRequestBodyInParameters) {
            testCaseListener.reportResultError(log, data, "%s has body".formatted(targetHttpMethod()),
                    "%s method should not have a body".formatted(targetHttpMethod()));
        } else {
            testCaseListener.reportResultInfo(log, data, "%s method does not have a body".formatted(targetHttpMethod()));
        }
    }

    private Operation getOperation(PathItem item) {
        return switch (targetHttpMethod()) {
            case DELETE -> item.getDelete();
            case GET -> item.getGet();
            case HEAD -> item.getHead();
            default -> null;
        };
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath();
    }

    @Override
    public String description() {
        return "checks if %s methods have a body".formatted(targetHttpMethod());
    }
}
