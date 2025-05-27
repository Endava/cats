package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Linter
@Singleton
public class OperationIdVerbPrefixLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final NamingArguments namingArguments;

    public OperationIdVerbPrefixLinter(TestCaseListener tcl, NamingArguments namingArguments) {
        super(tcl);
        this.namingArguments = namingArguments;
    }

    @Override
    public void process(FuzzingData data) {
        Map<String, List<String>> prefixMappings = namingArguments.getOperationPrefixMappings();
        List<String> allowedPrefixes = prefixMappings.get(data.getMethod().name().toLowerCase(Locale.ROOT));

        testCaseListener.addScenario(log, "Check if the operationId starts with an allowed prefix: {}", allowedPrefixes);
        testCaseListener.addExpectedResult(log, "OperationId begins with one of the allowed prefixes");

        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());

        String operationId = operation.getOperationId();

        if (StringUtils.isBlank(operationId) || doesNotMatchAllowedPrefixes(allowedPrefixes, operationId)) {
            testCaseListener.reportResultError(log, data, "OperationId prefix mismatch",
                    "OperationId [{}] does not start with any allowed prefix {}", operationId, allowedPrefixes);
        } else {
            testCaseListener.reportResultInfo(log, data, "OperationId [{}] uses an allowed prefix", operationId);
        }
    }

    private static boolean doesNotMatchAllowedPrefixes(List<String> allowedPrefixes, String operationId) {
        return allowedPrefixes.stream()
                .noneMatch(p -> operationId.toLowerCase(Locale.ROOT).startsWith(p.toLowerCase(Locale.ROOT)));
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that each operationId starts with one of the allowed prefixes per HTTP method, based on configuration";
    }
}