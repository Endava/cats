package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks if the OpenAPI contract paths have the recommended http response codes.
 */
@Linter
@Singleton
public class RecommendedHttpCodesLinter extends BaseLinter {
    private static final Set<String> REDIRECT_CODES = Set.of("301", "302", "303", "307", "308", "3XX", "3xx");
    private static final Set<String> SERVER_ERROR_ALTERNATIVES = Set.of("500", "502", "503", "504", "5XX", "5xx", "default");
    private static final Set<String> CLIENT_VALIDATION_ALTERNATIVES = Set.of("400", "422", "4XX", "4xx", "default");
    private static final Set<String> NOT_FOUND_ALTERNATIVES = Set.of("404", "4XX", "4xx", "default");

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new RecommendedHttpCodesLinter instance.
     *
     * @param tcl the test case listener
     */
    public RecommendedHttpCodesLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the current path contains all recommended HTTP response codes for HTTP method {}", data.getMethod());
        testCaseListener.addExpectedResult(log, "The following response codes should be present for HTTP operation {}: {}", data.getMethod(), data.getMethod().getRecommendedCodes());

        Set<String> definedCodes = data.getResponseCodes() != null ? data.getResponseCodes() : Collections.emptySet();

        List<String> missingCodes = data.getMethod().getRecommendedCodes()
                .stream()
                .filter(code -> isRequiredForContext(code, data))
                .filter(code -> !isCodeSatisfied(code, definedCodes))
                .toList();

        if (missingCodes.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All recommended HTTP codes are defined!");
        } else {
            testCaseListener.reportResultError(log, data, "Missing recommended HTTP response codes", "The following recommended HTTP response codes are missing: {}", missingCodes);
        }
    }

    private static boolean isRequiredForContext(String requiredCodeRule, FuzzingData data) {
        if ("404".equals(requiredCodeRule)
                && (data.getMethod() == HttpMethod.GET || data.getMethod() == HttpMethod.HEAD)
                && data.getPathItem() != null
                && HttpMethod.getOperation(data.getMethod(), data.getPathItem()) != null) {
            return isItemLookupPath(data.getPath());
        }
        return true;
    }

    private static boolean isItemLookupPath(String path) {
        return path != null && path.endsWith("}");
    }

    private static boolean isCodeSatisfied(String requiredCodeRule, Set<String> definedCodes) {
        if (Arrays.stream(requiredCodeRule.split(Pattern.quote("|"))).anyMatch(definedCodes::contains)) {
            return true;
        }
        if ("400".equals(requiredCodeRule)) {
            return definedCodes.stream().anyMatch(CLIENT_VALIDATION_ALTERNATIVES::contains);
        }
        if ("404".equals(requiredCodeRule)) {
            return definedCodes.stream().anyMatch(NOT_FOUND_ALTERNATIVES::contains);
        }
        if ("500".equals(requiredCodeRule)) {
            return definedCodes.stream().anyMatch(SERVER_ERROR_ALTERNATIVES::contains);
        }
        if (requiredCodeRule.startsWith("200") && !"200".equals(requiredCodeRule)) {
            return definedCodes.contains("204")
                    || definedCodes.contains("2XX")
                    || definedCodes.contains("2xx")
                    || definedCodes.stream().anyMatch(REDIRECT_CODES::contains);
        }
        return false;
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that the current path contains all recommended HTTP response codes for all operations";
    }
}
