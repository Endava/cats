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

import java.util.Arrays;
import java.util.Collections;
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
        List<String> allowedPrefixes = prefixMappings != null
                ? prefixMappings.getOrDefault(data.getMethod().name().toLowerCase(Locale.ROOT), Collections.emptyList())
                : Collections.emptyList();

        testCaseListener.addScenario(log, "Check if the operationId starts with an allowed prefix: {}", allowedPrefixes);
        testCaseListener.addExpectedResult(log, "OperationId begins with one of the allowed prefixes");

        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());
        if (operation == null) {
            testCaseListener.skipTest(log, "%s method not present".formatted(data.getMethod()));
            return;
        }

        String operationId = operation.getOperationId();

        String lastPartOfPath = data.getPath().substring(data.getPath().lastIndexOf('/') + 1);

        if (StringUtils.isBlank(operationId) || doesNotMatchAllowedPrefixes(allowedPrefixes, operationId, lastPartOfPath)) {
            testCaseListener.reportResultError(log, data, "OperationId prefix mismatch",
                    "OperationId [{}] does not start with any allowed prefix {}", operationId, allowedPrefixes);
        } else {
            testCaseListener.reportResultInfo(log, data, "OperationId [{}] uses an allowed prefix", operationId);
        }
    }

    private static boolean doesNotMatchAllowedPrefixes(List<String> allowedPrefixes, String operationId, String lastPartOfPath) {
        String lowerOpId = operationId.toLowerCase(Locale.ROOT);
        String lowerLastPart = lastPartOfPath.toLowerCase(Locale.ROOT);
        boolean doesNotMatchPrefixes = allowedPrefixes.stream()
                .noneMatch(p -> lowerOpId.startsWith(p.toLowerCase(Locale.ROOT)));
        boolean pathDoesntStartsWithOperationId = !lowerLastPart.startsWith(lowerOpId);
        boolean operationIsDoesntStartsWithPath = !lowerOpId.startsWith(lowerLastPart);
        boolean doesNotMatchPathTokens = Arrays.stream(lowerLastPart.split("[-_]", -1))
                .filter(token -> token.length() >= 3)
                .noneMatch(lowerOpId::startsWith);

        return doesNotMatchPrefixes && pathDoesntStartsWithOperationId && operationIsDoesntStartsWithPath && doesNotMatchPathTokens;
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