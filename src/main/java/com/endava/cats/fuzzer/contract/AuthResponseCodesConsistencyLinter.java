package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Verifies that operations requiring authentication document 401/403 error responses,
 * and operations explicitly opting out of authentication (security: []) do not document 401.
 */
@Linter
@Singleton
public class AuthResponseCodesConsistencyLinter extends BaseLinter {
    private static final Set<String> AUTH_ERROR_CODES = Set.of("401", "403", "4XX", "4xx", "default");
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new AuthResponseCodesConsistencyLinter instance.
     *
     * @param tcl the test case listener
     */
    public AuthResponseCodesConsistencyLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check that 401/403 response codes align with the operation's security requirements for {} {}", data.getMethod(), data.getPath());
        testCaseListener.addExpectedResult(log, "Secured operations should document 401 or 403 responses, and public operations opting out of security (security: []) should not document 401");

        Operation operation = data.getPathItem() != null && data.getMethod() != null
                ? HttpMethod.getOperation(data.getMethod(), data.getPathItem())
                : null;
        if (operation == null) {
            testCaseListener.skipTest(log, "Operation not present");
            return;
        }

        List<SecurityRequirement> globalSec = data.getOpenApi() != null && data.getOpenApi().getSecurity() != null
                ? data.getOpenApi().getSecurity()
                : Collections.emptyList();
        List<SecurityRequirement> opSec = operation.getSecurity();

        boolean hasActiveGlobalSec = hasNonEmptySecurityRequirement(globalSec);
        boolean explicitlyPublic = opSec != null && !hasNonEmptySecurityRequirement(opSec) && hasActiveGlobalSec;
        boolean requiresAuth = opSec != null ? hasNonEmptySecurityRequirement(opSec) : hasActiveGlobalSec;

        Set<String> definedCodes = data.getResponseCodes() != null
                ? data.getResponseCodes()
                : (operation.getResponses() != null ? operation.getResponses().keySet() : Collections.emptySet());

        if (requiresAuth && definedCodes.stream().noneMatch(AUTH_ERROR_CODES::contains)) {
            testCaseListener.reportResultWarn(log, data, "Secured operation missing 401/403 response",
                    "Operation requires authentication via security schemes but does not document 401 or 403 response codes");
        } else if (explicitlyPublic && definedCodes.contains("401")) {
            testCaseListener.reportResultWarn(log, data, "Public operation documents 401 response",
                    "Operation explicitly opts out of authentication (security: []) but still documents a 401 response code");
        } else {
            testCaseListener.reportResultInfo(log, data, "Authentication response codes (401/403) are consistent with the operation's security requirements");
        }
    }

    private static boolean hasNonEmptySecurityRequirement(List<SecurityRequirement> requirements) {
        return requirements != null && requirements.stream().anyMatch(req -> req != null && !req.isEmpty());
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that secured operations document 401/403 error responses and public operations opting out of security (security: []) do not document 401";
    }
}
