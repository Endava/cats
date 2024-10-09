package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@LinterFuzzer
@Singleton
public class UniqueOperationIdsLinterFuzzer extends BaseLinterFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new UniqueOperationIdsLinterFuzzer instance.
     *
     * @param tcl the test case listener
     */
    public UniqueOperationIdsLinterFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Checks if all operationIds are unique");
        testCaseListener.addExpectedResult(log, "All operationIds must be unique");

        Map<String, Integer> operationIdCount = new HashMap<>();

        data.getOpenApi().getPaths().values()
                .forEach(pathItem -> pathItem.readOperations()
                        .stream()
                        .filter(operation -> StringUtils.isNotBlank(operation.getOperationId()))
                        .forEach(operation -> operationIdCount.merge(operation.getOperationId(), 1, Integer::sum)));
        List<String> duplicateOperations = operationIdCount.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();

        if (duplicateOperations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All operationIds are unique");
        } else {
            testCaseListener.reportResultError(log, data, "OperationId is not unique", "The following operationIds are not unique: {}", duplicateOperations);
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "1";
    }

    @Override
    public String description() {
        return "verifies that all operationIds are unique";
    }
}