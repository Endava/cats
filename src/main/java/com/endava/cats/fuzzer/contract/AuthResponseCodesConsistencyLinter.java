package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.Set;

/**
 * Verifies that operations requiring authentication document 401/403 error responses,
 * operations explicitly opting out of authentication (security: []) do not document 401,
 * specifications without security schemes do not document 401, and operations documenting 403 also document 401.
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

        reportAuthConsistency(data, operation, resolveDefinedCodes(data, operation));
    }

    private void reportAuthConsistency(FuzzingData data, Operation operation, Set<String> definedCodes) {
        boolean requiresAuth = requiresAuthentication(data.getOpenApi(), operation);
        boolean explicitlyPublic = CatsModelUtils.isExplicitlyPublic(operation);

        if (isMissingAuthErrorCodes(requiresAuth, definedCodes)) {
            testCaseListener.reportResultWarn(log, data, "Secured operation missing 401/403 response",
                    "Operation requires authentication via security schemes but does not document 401 or 403 response codes");
        } else if (documents401WhenExplicitlyPublic(explicitlyPublic, definedCodes)) {
            testCaseListener.reportResultWarn(log, data, "Public operation documents 401 response",
                    "Operation explicitly opts out of authentication (security: []) but still documents a 401 response code");
        } else if (documents401WithoutSecuritySchemes(data.getOpenApi(), requiresAuth, definedCodes)) {
            testCaseListener.reportResultWarn(log, data, "401 documented but the spec defines no security schemes",
                    "Operation documents a 401 response code, but the specification defines no security schemes in components.securitySchemes");
        } else if (documents403Without401(explicitlyPublic, definedCodes)) {
            testCaseListener.reportResultWarn(log, data, "Operation documents 403 but not 401",
                    "Operation documents a 403 response code without documenting 401 (or 4XX) for unauthenticated requests");
        } else {
            testCaseListener.reportResultInfo(log, data, "Authentication response codes (401/403) are consistent with the operation's security requirements");
        }
    }

    private static Set<String> resolveDefinedCodes(FuzzingData data, Operation operation) {
        if (data.getResponseCodes() != null) {
            return data.getResponseCodes();
        }
        return operation.getResponses() != null ? operation.getResponses().keySet() : Collections.emptySet();
    }

    private static boolean requiresAuthentication(OpenAPI openAPI, Operation operation) {
        if (operation.getSecurity() != null) {
            return CatsModelUtils.hasNonEmptySecurityRequirement(operation.getSecurity());
        }
        return openAPI != null && CatsModelUtils.hasNonEmptySecurityRequirement(openAPI.getSecurity());
    }

    private static boolean isMissingAuthErrorCodes(boolean requiresAuth, Set<String> definedCodes) {
        return requiresAuth && definedCodes.stream().noneMatch(AUTH_ERROR_CODES::contains);
    }

    private static boolean documents401WhenExplicitlyPublic(boolean explicitlyPublic, Set<String> definedCodes) {
        return explicitlyPublic && definedCodes.contains("401");
    }

    private static boolean documents401WithoutSecuritySchemes(OpenAPI openAPI, boolean requiresAuth, Set<String> definedCodes) {
        return openAPI != null && !requiresAuth && !CatsModelUtils.hasSecuritySchemes(openAPI) && definedCodes.contains("401");
    }

    private static boolean documents403Without401(boolean explicitlyPublic, Set<String> definedCodes) {
        return !explicitlyPublic && definedCodes.contains("403") && !CatsModelUtils.declares401(definedCodes);
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
