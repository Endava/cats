package com.endava.cats.fuzzer.contract.base;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;

import java.util.List;
import java.util.Optional;

public abstract class AbstractRequestWithoutBodyLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new instance of subclasses.
     *
     * @param tcl the test case listener
     */
    protected AbstractRequestWithoutBodyLinter(TestCaseListener tcl) {
        super(tcl);
    }

    protected abstract HttpMethod targetHttpMethod();


    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if %s methods have a body".formatted(targetHttpMethod()));
        testCaseListener.addExpectedResult(log, "%s methods must not have a body".formatted(targetHttpMethod()));

        if (data.getMethod() != null && data.getMethod() != targetHttpMethod()) {
            testCaseListener.skipTest(log, "%s method not matching target %s".formatted(data.getMethod(), targetHttpMethod()));
            return;
        }

        Operation operation = HttpMethod.getOperation(targetHttpMethod(), data.getPathItem());
        if (operation == null) {
            testCaseListener.skipTest(log, "%s method not present".formatted(targetHttpMethod()));
            return;
        }

        boolean hasRequestBodyInRoot = operation.getRequestBody() != null;
        boolean hasRequestBodyInParameters = Optional.ofNullable(operation.getParameters())
                .orElse(List.of())
                .stream()
                .anyMatch(param -> param != null && ("body".equals(param.getName()) || "body".equalsIgnoreCase(param.getIn())));

        if (hasRequestBodyInRoot || hasRequestBodyInParameters) {
            testCaseListener.reportResultError(log, data, "%s has body".formatted(targetHttpMethod()),
                    "%s method should not have a body".formatted(targetHttpMethod()));
        } else {
            testCaseListener.reportResultInfo(log, data, "%s method does not have a body".formatted(targetHttpMethod()));
        }
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return HttpMethod.restMethods().stream()
                .filter(method -> method != targetHttpMethod())
                .toList();
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + targetHttpMethod();
    }

    @Override
    public String description() {
        return "checks if %s methods have a body".formatted(targetHttpMethod());
    }
}
