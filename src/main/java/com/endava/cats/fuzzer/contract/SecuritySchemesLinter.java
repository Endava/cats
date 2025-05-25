package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checks that the OpenAPI Contract has security schemes defined either at global level or path level.
 */
@Linter
@Singleton
public class SecuritySchemesLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new SecuritySchemesLinter instance.
     *
     * @param tcl the test case listener
     */
    public SecuritySchemesLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the current path has security schemes defined either globally or at HTTP method level for {}", data.getMethod());
        testCaseListener.addExpectedResult(log, "At least one security scheme must be present either globally or at HTTP method level");

        Map<String, SecurityScheme> securitySchemeMap = Optional.ofNullable(data.getOpenApi().getComponents()).orElse(new Components()).getSecuritySchemes();
        List<SecurityRequirement> securityRequirementList = Optional.ofNullable(data.getOpenApi().getSecurity()).orElse(Collections.emptyList());

        boolean hasTopLevelSecuritySchemes = !securityRequirementList.isEmpty();
        boolean areGlobalSecuritySchemesDefined = securityRequirementList.stream().allMatch(securityRequirement -> Optional.ofNullable(securitySchemeMap).orElse(Collections.emptyMap()).keySet().containsAll(securityRequirement.keySet()));

        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());

        boolean hasSecuritySchemesAtTagLevel = !Optional.ofNullable(operation.getSecurity()).orElse(Collections.emptyList()).isEmpty();

        if (hasTopLevelSecuritySchemes || hasSecuritySchemesAtTagLevel) {
            if (areGlobalSecuritySchemesDefined) {
                testCaseListener.reportResultInfo(log, data, "The current path has security scheme(s) properly defined");
            } else {
                testCaseListener.reportResultWarn(log, data, "Security scheme not defined", "The current path has security scheme(s) defined, but they are not present in the [components->securitySchemes] contract element");
            }
        } else {
            testCaseListener.reportResultError(log, data, "No security scheme defined", "The current path does not have security scheme(s) defined and there are none defined globally");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies if the OpenApi contract contains valid security schemas for all paths, either globally configured or per path";
    }
}
