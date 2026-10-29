package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that each operation defines a non-blank operationId and a meaningful summary or description.
 */
@Linter
@Singleton
public class MissingOperationMetadataLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new MissingOperationMetadataLinter instance.
     *
     * @param tcl the test case listener
     */
    public MissingOperationMetadataLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check that operation {} {} defines an operationId and either a summary or description", data.getMethod(), data.getPath());
        testCaseListener.addExpectedResult(log, "Every operation should define a non-blank operationId and a summary or description");

        Operation operation = data.getPathItem() != null && data.getMethod() != null
                ? HttpMethod.getOperation(data.getMethod(), data.getPathItem())
                : null;
        if (operation == null) {
            testCaseListener.skipTest(log, "Operation not present");
            return;
        }

        List<String> violations = new ArrayList<>();
        if (StringUtils.isBlank(operation.getOperationId())) {
            violations.add("Operation is missing 'operationId'");
        }
        if (StringUtils.isBlank(operation.getSummary()) && StringUtils.isBlank(operation.getDescription())) {
            violations.add("Operation is missing both 'summary' and 'description'");
        }

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "Operation defines operationId and summary/description metadata");
        } else {
            testCaseListener.reportResultWarn(log, data, "Missing operation metadata", String.join("; ", violations));
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that every operation defines a non-blank operationId and at least one of summary or description";
    }
}
